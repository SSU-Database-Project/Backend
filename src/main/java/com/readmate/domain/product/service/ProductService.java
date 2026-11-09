package com.readmate.domain.product.service;

import static com.readmate.domain.product.repository.ProductSpecs.brandEq;
import static com.readmate.domain.product.repository.ProductSpecs.categoryIn;
import static com.readmate.domain.product.repository.ProductSpecs.nameContains;
import static com.readmate.domain.product.repository.ProductSpecs.sellerEq;
import static com.readmate.domain.product.repository.ProductSpecs.statusIn;

import com.readmate.domain.brand.entity.Brand;
import com.readmate.domain.brand.repository.BrandRepository;
import com.readmate.domain.category.entity.Category;
import com.readmate.domain.category.service.CategoryService;
import com.readmate.domain.inventory.entity.Inventory;
import com.readmate.domain.inventory.entity.StockHistory;
import com.readmate.domain.inventory.repository.InventoryRepository;
import com.readmate.domain.inventory.repository.StockHistoryRepository;
import com.readmate.domain.product.dto.ProductDtos.ProductCreateRequest;
import com.readmate.domain.product.dto.ProductDtos.ProductDetailResponse;
import com.readmate.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.readmate.domain.product.dto.ProductDtos.ProductUpdateRequest;
import com.readmate.domain.product.dto.ProductSort;
import com.readmate.domain.product.entity.Product;
import com.readmate.domain.product.entity.ProductStatus;
import com.readmate.domain.product.repository.ProductRepository;
import com.readmate.domain.seller.entity.Seller;
import com.readmate.domain.seller.service.SellerService;
import com.readmate.global.common.PageResponse;
import com.readmate.global.error.BusinessException;
import com.readmate.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final BrandRepository brandRepository;
    private final CategoryService categoryService;
    private final SellerService sellerService;

    // ===== 공개 조회 =====

    /**
     * 상품 목록 (카테고리·브랜드·키워드 필터, 정렬, 페이징). 실행되는 쿼리:
     *  1) products LEFT JOIN brands JOIN categories ... LIMIT  (EntityGraph fetch join)
     *  2) count(*)                                             (조인 없음)
     *  3) inventory WHERE product_id IN (...)                  (재고 일괄 조회)
     * → 상품 수와 무관하게 쿼리 3번 (N+1 없음)
     */
    public PageResponse<ProductSummaryResponse> searchPublic(Long categoryId, Long brandId, String keyword,
                                                             String sort, int page, int size) {
        List<Specification<Product>> specs = new ArrayList<>();
        specs.add(statusIn(ProductStatus.PUBLIC_STATUSES));
        if (categoryId != null) {
            specs.add(categoryIn(categoryService.resolveCategoryIds(categoryId)));
        }
        if (brandId != null) {
            specs.add(brandEq(brandId));
        }
        if (keyword != null && !keyword.isBlank()) {
            specs.add(nameContains(keyword.trim()));
        }
        Page<Product> result = productRepository.findAll(Specification.allOf(specs),
                PageRequest.of(page, size, ProductSort.from(sort).getSort()));
        return toSummaryPage(result);
    }

    /** 공개 상세: HIDDEN / BLOCKED 상품은 존재하지 않는 것처럼 404 */
    public ProductDetailResponse getPublicDetail(Long productId) {
        Product product = productRepository.findDetailById(productId)
                .filter(p -> p.getStatus().isPublic())
                .orElseThrow(() -> BusinessException.notFound("상품"));
        return ProductDetailResponse.of(product, stockOf(productId));
    }

    // ===== 판매자 =====

    /**
     * 상품 등록 트랜잭션: products → inventory → stock_history(IN) 를 한 트랜잭션에서 생성.
     * 하나라도 실패하면 전체 롤백 — "재고 행이 없는 상품"이 생기면 주문/재고 로직이 모두 깨지기 때문.
     */
    @Transactional
    public ProductDetailResponse create(Long userId, ProductCreateRequest request) {
        Seller seller = sellerService.getApprovedSeller(userId);
        Category category = getLeafCategory(request.categoryId());
        Brand brand = request.brandId() == null ? null : getUsableBrand(request.brandId(), seller.getId());

        Product product = productRepository.save(Product.create(seller, brand, category, request.name().trim(),
                request.description(), request.price(), request.imageUrl()));

        int initialStock = request.initialStock();
        inventoryRepository.save(Inventory.create(product, initialStock));
        if (initialStock > 0) {
            stockHistoryRepository.save(StockHistory.inbound(product, initialStock, initialStock, "초기 재고"));
        }
        return ProductDetailResponse.of(product, initialStock);
    }

    @Transactional
    public ProductDetailResponse update(Long userId, Long productId, ProductUpdateRequest request) {
        Seller seller = sellerService.getApprovedSeller(userId);
        Product product = getOwnedProduct(productId, seller.getId());

        if (request.name() != null) {
            product.changeName(request.name().trim());
        }
        if (request.description() != null) {
            product.changeDescription(request.description());
        }
        if (request.price() != null) {
            product.changePrice(request.price()); // 과거 주문은 order_items.unit_price 스냅샷이라 영향 없음
        }
        if (request.imageUrl() != null) {
            product.changeImageUrl(request.imageUrl());
        }
        if (request.categoryId() != null) {
            product.changeCategory(getLeafCategory(request.categoryId()));
        }
        if (Boolean.TRUE.equals(request.removeBrand())) {
            product.changeBrand(null);
        } else if (request.brandId() != null) {
            product.changeBrand(getUsableBrand(request.brandId(), seller.getId()));
        }
        if (request.status() != null) {
            changeStatusBySeller(product, request.status());
        }
        return ProductDetailResponse.of(product, stockOf(productId));
    }

    /** 판매자 센터 내 상품 목록: 모든 상태(BLOCKED 포함) — idx_products_seller_created 사용 */
    public PageResponse<ProductSummaryResponse> myProducts(Long userId, ProductStatus status, int page, int size) {
        Seller seller = sellerService.getApprovedSeller(userId);
        List<Specification<Product>> specs = new ArrayList<>();
        specs.add(sellerEq(seller.getId()));
        if (status != null) {
            specs.add(statusIn(EnumSet.of(status)));
        }
        Page<Product> result = productRepository.findAll(Specification.allOf(specs),
                PageRequest.of(page, size, ProductSort.LATEST.getSort()));
        return toSummaryPage(result);
    }

    // ===== 관리자 =====

    public PageResponse<ProductSummaryResponse> adminSearch(ProductStatus status, String keyword, int page, int size) {
        List<Specification<Product>> specs = new ArrayList<>();
        if (status != null) {
            specs.add(statusIn(EnumSet.of(status)));
        }
        if (keyword != null && !keyword.isBlank()) {
            specs.add(nameContains(keyword.trim()));
        }
        Page<Product> result = productRepository.findAll(Specification.allOf(specs),
                PageRequest.of(page, size, ProductSort.LATEST.getSort()));
        return toSummaryPage(result);
    }

    /** 관리자는 모든 상태로 변경 가능 (BLOCKED 지정/해제는 관리자만) */
    @Transactional
    public ProductDetailResponse adminChangeStatus(Long productId, ProductStatus status) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> BusinessException.notFound("상품"));
        product.changeStatus(status);
        return ProductDetailResponse.of(product, stockOf(productId));
    }

    // ===== 내부 검증 =====

    private void changeStatusBySeller(Product product, ProductStatus status) {
        if (product.isBlocked()) {
            throw new BusinessException(ErrorCode.PRODUCT_BLOCKED);
        }
        if (!ProductStatus.SELLER_SETTABLE.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "판매자는 ON_SALE, SOLD_OUT, HIDDEN 만 지정할 수 있습니다.");
        }
        product.changeStatus(status);
    }

    /** 소유권 검증: 타인 상품 수정 시도는 403 */
    private Product getOwnedProduct(Long productId, Long sellerId) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> BusinessException.notFound("상품"));
        if (!product.isOwnedBy(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }

    /**
     * 브랜드 소유권 + 승인 여부 검증.
     * 소유권은 DB 의 복합 FK (brand_id, seller_id) → brands(id, seller_id) 도 막지만,
     * 사용자에게 의미 있는 에러(403 BRAND_NOT_OWNED)를 주기 위해 서비스에서 먼저 확인한다.
     * 승인 여부는 다른 행의 값에 의존하므로 CHECK 로 표현할 수 없어 서비스에서만 검증한다.
     */
    private Brand getUsableBrand(Long brandId, Long sellerId) {
        Brand brand = brandRepository.findById(brandId).orElseThrow(() -> BusinessException.notFound("브랜드"));
        if (!brand.isOwnedBy(sellerId)) {
            throw new BusinessException(ErrorCode.BRAND_NOT_OWNED);
        }
        if (!brand.isApproved()) {
            throw new BusinessException(ErrorCode.BRAND_NOT_APPROVED);
        }
        return brand;
    }

    private Category getLeafCategory(Long categoryId) {
        Category category = categoryService.getCategory(categoryId);
        if (!category.isLeaf()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_LEAF);
        }
        return category;
    }

    private PageResponse<ProductSummaryResponse> toSummaryPage(Page<Product> result) {
        Map<Long, Integer> stocks = stocksOf(result.getContent());
        return PageResponse.of(result,
                p -> ProductSummaryResponse.of(p, stocks.getOrDefault(p.getId(), 0)));
    }

    /** 재고 일괄 조회: inventory WHERE product_id IN (...) — PK 검색 */
    private Map<Long, Integer> stocksOf(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        return inventoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Inventory::getQuantity));
    }

    private int stockOf(Long productId) {
        return inventoryRepository.findById(productId).map(Inventory::getQuantity).orElse(0);
    }
}
