package com.ssu.shop.domain.product.service;

import static com.ssu.shop.domain.product.repository.ProductSpecs.brandEq;
import static com.ssu.shop.domain.product.repository.ProductSpecs.categoryIn;
import static com.ssu.shop.domain.product.repository.ProductSpecs.keywordContains;
import static com.ssu.shop.domain.product.repository.ProductSpecs.sellerEq;
import static com.ssu.shop.domain.product.repository.ProductSpecs.statusIn;
import static com.ssu.shop.domain.product.repository.ProductSpecs.statusNot;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.repository.BrandRepository;
import com.ssu.shop.domain.category.entity.Category;
import com.ssu.shop.domain.category.service.CategoryService;
import com.ssu.shop.domain.inventory.entity.StockHistory;
import com.ssu.shop.domain.inventory.repository.StockHistoryRepository;
import com.ssu.shop.domain.product.dto.ProductDtos.AdminProductStatusRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.OptionRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductCreateRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductDetailResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductUpdateRequest;
import com.ssu.shop.domain.product.dto.ProductSort;
import com.ssu.shop.domain.product.entity.Color;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.entity.Size;
import com.ssu.shop.domain.product.repository.ColorRepository;
import com.ssu.shop.domain.product.repository.ProductOptionRepository;
import com.ssu.shop.domain.product.repository.ProductOptionRepository.StockSum;
import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.product.repository.SizeRepository;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.DbConstraints;
import com.ssu.shop.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ProductOptionRepository optionRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final BrandRepository brandRepository;
    private final CategoryService categoryService;
    private final SellerService sellerService;

    // ===== 구매자 조회 (로그인 필요) =====

    /**
     * 상품 목록 (카테고리·브랜드·검색어, 정렬, 페이징). 승인된 상품(ON_SALE, SOLD_OUT)만. 실행되는 쿼리:
     *  1) products JOIN brands JOIN categories ... LIMIT     (EntityGraph fetch join)
     *  2) count(*)
     *  3) product_options WHERE product_id IN (...) GROUP BY (총 재고 일괄 조회)
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
            specs.add(keywordContains(keyword.trim()));
        }
        Page<Product> result = productRepository.findAll(Specification.allOf(specs),
                PageRequest.of(page, size, ProductSort.from(sort).getSort()));
        return toSummaryPage(result);
    }

    /** 구매자 상세: 승인 전·거절·차단·삭제 상품은 존재하지 않는 것처럼 404 */
    public ProductDetailResponse getPublicDetail(Long productId) {
        Product product = productRepository.findDetailById(productId)
                .filter(p -> p.getStatus().isPublic())
                .orElseThrow(() -> BusinessException.notFound("상품"));
        return toDetail(product);
    }

    // ===== 판매자 =====

    /**
     * 상품 등록 트랜잭션: products(PENDING) → product_options → stock_history(IN) 를 한 트랜잭션에서 생성.
     * 하나라도 실패하면 전체 롤백 — 옵션 없는 상품이나 이력 없는 재고가 생기지 않는다.
     */
    @Transactional
    public ProductDetailResponse create(Long userId, ProductCreateRequest request) {
        Seller seller = sellerService.getSeller(userId);
        Brand brand = getUsableBrand(request.brandId(), seller.getId());
        Category category = getLeafCategory(request.categoryId());
        String productCode = request.productCode().trim();
        if (productRepository.existsByBrand_IdAndProductCode(brand.getId(), productCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE);
        }

        Product product = Product.create(brand, category, productCode, request.name().trim(),
                request.description(), request.price(), request.gender(), request.season(), request.shipDays(),
                request.imageUrl());
        try {
            productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE); // 동시 등록 → uk_products_brand_code
        }
        addOptions(product, category, request.options(), Set.of());
        return toDetail(product);
    }

    /**
     * 상품 수정. 정보가 하나라도 바뀌면 재승인(PENDING)으로 돌아가 승인 전까지 구매자에게 숨긴다(회의 결정).
     * 차단(BLOCKED)·삭제(DELETED) 상품은 수정할 수 없다.
     */
    @Transactional
    public ProductDetailResponse update(Long userId, Long productId, ProductUpdateRequest request) {
        Seller seller = sellerService.getSeller(userId);
        Product product = getOwnedProduct(productId, seller.getId());
        if (!product.isEditable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE);
        }
        List<ProductOption> existingOptions = optionRepository.findAllWithColorAndSize(productId);
        boolean changed = false;

        if (request.categoryId() != null && !request.categoryId().equals(product.getCategory().getId())) {
            Category category = getLeafCategory(request.categoryId());
            boolean sizeMismatch = existingOptions.stream()
                    .anyMatch(o -> o.getSize().getSizeGroup() != category.getSizeGroup());
            if (sizeMismatch) {
                throw new BusinessException(ErrorCode.INVALID_OPTION, "사이즈 체계가 다른 카테고리로는 옮길 수 없습니다.");
            }
            product.changeCategory(category);
            changed = true;
        }
        if (request.productCode() != null && !request.productCode().trim().equals(product.getProductCode())) {
            String code = request.productCode().trim();
            if (productRepository.existsByBrand_IdAndProductCode(product.getBrand().getId(), code)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE);
            }
            product.changeProductCode(code);
            changed = true;
        }
        if (request.name() != null && !request.name().trim().equals(product.getName())) {
            product.changeName(request.name().trim());
            changed = true;
        }
        if (request.description() != null && !request.description().equals(product.getDescription())) {
            product.changeDescription(request.description());
            changed = true;
        }
        if (request.price() != null && !request.price().equals(product.getPrice())) {
            product.changePrice(request.price()); // 과거 주문은 order_items.unit_price 스냅샷이라 영향 없음
            changed = true;
        }
        if (request.gender() != null && request.gender() != product.getGender()) {
            product.changeGender(request.gender());
            changed = true;
        }
        if (request.season() != null && !request.season().equals(product.getSeason())) {
            product.changeSeason(request.season());
            changed = true;
        }
        if (request.shipDays() != null && !request.shipDays().equals(product.getShipDays())) {
            product.changeShipDays(request.shipDays());
            changed = true;
        }
        if (request.imageUrl() != null && !request.imageUrl().equals(product.getImageUrl())) {
            product.changeImageUrl(request.imageUrl());
            changed = true;
        }
        if (request.addOptions() != null && !request.addOptions().isEmpty()) {
            Set<String> existingKeys = existingOptions.stream()
                    .map(o -> key(o.getColor().getId(), o.getSize().getId()))
                    .collect(Collectors.toSet());
            addOptions(product, product.getCategory(), request.addOptions(), existingKeys);
            changed = true;
        }

        if (changed) {
            product.requestReapproval();
        }
        try {
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE);
        }
        return toDetail(product);
    }

    /** 판매 상태 토글 (승인 불필요): 승인된 상품만 ON_SALE ↔ SOLD_OUT */
    @Transactional
    public ProductDetailResponse changeSaleStatus(Long userId, Long productId, ProductStatus status) {
        Seller seller = sellerService.getSeller(userId);
        Product product = getOwnedProduct(productId, seller.getId());
        if (!ProductStatus.SELLER_SALE_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "판매자는 ON_SALE, SOLD_OUT 만 지정할 수 있습니다.");
        }
        if (!ProductStatus.SELLER_SALE_STATUSES.contains(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_APPROVED);
        }
        product.changeSaleStatus(status);
        return toDetail(product);
    }

    /** 상품 삭제 (승인 불필요, 회의 결정). 주문 기록이 참조하므로 행은 남기고 DELETED 로 바꾼다. */
    @Transactional
    public void delete(Long userId, Long productId) {
        Seller seller = sellerService.getSeller(userId);
        Product product = getOwnedProduct(productId, seller.getId());
        if (product.getStatus() == ProductStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE);
        }
        product.delete();
    }

    /** 판매자 센터 내 상품 목록 (삭제 제외, 모든 상태 — 승인 대기·거절 사유 확인용) */
    public PageResponse<ProductSummaryResponse> myProducts(Long userId, ProductStatus status, int page, int size) {
        Seller seller = sellerService.getSeller(userId);
        List<Specification<Product>> specs = new ArrayList<>();
        specs.add(sellerEq(seller.getId()));
        specs.add(status != null ? statusIn(EnumSet.of(status)) : statusNot(ProductStatus.DELETED));
        Page<Product> result = productRepository.findAll(Specification.allOf(specs),
                PageRequest.of(page, size, ProductSort.LATEST.getSort()));
        return toSummaryPage(result);
    }

    /** 판매자 상세 (승인 전·거절 상품도 본인 것이면 조회) */
    public ProductDetailResponse getMyProduct(Long userId, Long productId) {
        Seller seller = sellerService.getSeller(userId);
        return toDetail(getOwnedProduct(productId, seller.getId()));
    }

    // ===== 관리자 =====

    /** 관리자 목록. status=PENDING 으로 승인 대기 목록 (idx_products_status_created) */
    public PageResponse<ProductSummaryResponse> adminSearch(ProductStatus status, String keyword, int page, int size) {
        List<Specification<Product>> specs = new ArrayList<>();
        if (status != null) {
            specs.add(statusIn(EnumSet.of(status)));
        }
        if (keyword != null && !keyword.isBlank()) {
            specs.add(keywordContains(keyword.trim()));
        }
        Page<Product> result = productRepository.findAll(Specification.allOf(specs),
                PageRequest.of(page, size, ProductSort.OLDEST.getSort()));
        return toSummaryPage(result);
    }

    public ProductDetailResponse adminDetail(Long productId) {
        return toDetail(productRepository.findDetailById(productId)
                .orElseThrow(() -> BusinessException.notFound("상품")));
    }

    /**
     * 승인 / 거절 / 차단 / 차단 해제. 허용되는 이전 상태를 WHERE 에 넣은 조건부 UPDATE 라서
     * 관리자 두 명이 동시에 처리하거나 그 사이 판매자가 상태를 바꿔도 잘못된 전이가 일어나지 않는다.
     */
    @Transactional
    public ProductDetailResponse adminChangeStatus(Long productId, AdminProductStatusRequest request) {
        ProductStatus to = request.status();
        Set<ProductStatus> from = switch (to) {
            case ON_SALE -> EnumSet.of(ProductStatus.PENDING, ProductStatus.BLOCKED);
            case REJECTED -> EnumSet.of(ProductStatus.PENDING);
            case BLOCKED -> EnumSet.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "관리자는 ON_SALE(승인·차단 해제), REJECTED(거절), BLOCKED(차단) 만 지정할 수 있습니다.");
        };
        String rejectReason = to == ProductStatus.REJECTED ? request.rejectReason() : null;
        int updated = productRepository.transitStatus(productId, from, to, rejectReason);
        if (updated == 0) {
            if (!productRepository.existsById(productId)) {
                throw BusinessException.notFound("상품");
            }
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        return adminDetail(productId);
    }

    // ===== 내부 =====

    /**
     * 옵션 검증 후 저장 + 초기 재고 입고 이력.
     * - 색상·사이즈는 고정 목록에 있어야 한다 (FK 도 막지만 친절한 에러를 위해 먼저 확인)
     * - 사이즈는 카테고리의 사이즈 체계(size_group)와 같아야 한다 → 다른 테이블 값에 의존해 CHECK 로 못 하므로 서비스 검증
     * - 같은 색상·사이즈 조합은 한 번만 (uk_product_options_combination 도 막는다)
     */
    private void addOptions(Product product, Category category, List<OptionRequest> requests,
                            Set<String> existingKeys) {
        Map<Long, Color> colors = colorRepository.findAllById(
                        requests.stream().map(OptionRequest::colorId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Color::getId, Function.identity()));
        Map<Long, Size> sizes = sizeRepository.findAllById(
                        requests.stream().map(OptionRequest::sizeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Size::getId, Function.identity()));

        Set<String> seen = new HashSet<>(existingKeys);
        for (OptionRequest req : requests) {
            Color color = colors.get(req.colorId());
            Size size = sizes.get(req.sizeId());
            if (color == null || size == null) {
                throw new BusinessException(ErrorCode.INVALID_OPTION, "없는 색상 또는 사이즈입니다.");
            }
            if (size.getSizeGroup() != category.getSizeGroup()) {
                throw new BusinessException(ErrorCode.INVALID_OPTION,
                        "이 카테고리는 " + category.getSizeGroup() + " 사이즈만 쓸 수 있습니다.");
            }
            if (!seen.add(key(color.getId(), size.getId()))) {
                throw new BusinessException(ErrorCode.INVALID_OPTION, "같은 색상·사이즈 조합이 중복되었습니다.");
            }
            ProductOption option = optionRepository.save(ProductOption.create(product, color, size, req.stock()));
            if (req.stock() > 0) {
                stockHistoryRepository.save(StockHistory.inbound(option, req.stock(), req.stock(), "초기 재고"));
            }
        }
        try {
            optionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (DbConstraints.isViolated(e, "uk_product_options_combination")) {
                throw new BusinessException(ErrorCode.INVALID_OPTION, "같은 색상·사이즈 조합이 이미 있습니다.");
            }
            throw e;
        }
    }

    private static String key(Long colorId, Long sizeId) {
        return colorId + ":" + sizeId;
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

    /** 브랜드 소유권 + 승인 여부. 승인 여부는 다른 행의 값이라 CHECK 로 표현할 수 없어 서비스에서 검증한다. */
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

    private ProductDetailResponse toDetail(Product product) {
        return ProductDetailResponse.of(product, optionRepository.findAllWithColorAndSize(product.getId()));
    }

    /** 다른 도메인(좋아요 목록 등)이 상품 목록을 같은 모양으로 보여줄 때 사용. 총 재고는 IN 일괄 조회 1번 */
    public List<ProductSummaryResponse> summarize(List<Product> products) {
        Map<Long, Integer> stocks = totalStocksOf(products);
        return products.stream().map(p -> ProductSummaryResponse.of(p, stocks.getOrDefault(p.getId(), 0))).toList();
    }

    private PageResponse<ProductSummaryResponse> toSummaryPage(Page<Product> result) {
        Map<Long, Integer> stocks = totalStocksOf(result.getContent());
        return PageResponse.of(result, p -> ProductSummaryResponse.of(p, stocks.getOrDefault(p.getId(), 0)));
    }

    /** 총 재고 일괄 조회: product_options WHERE product_id IN (...) GROUP BY product_id */
    private Map<Long, Integer> totalStocksOf(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        return optionRepository.sumStockByProductIds(ids).stream()
                .filter(s -> Objects.nonNull(s.getTotalStock()))
                .collect(Collectors.toMap(StockSum::getProductId, s -> s.getTotalStock().intValue()));
    }
}
