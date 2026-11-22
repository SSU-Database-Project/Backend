package com.ssu.shop.domain.brand.service;

import com.ssu.shop.domain.brand.dto.BrandDtos.AdminBrandResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandCreateRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandDetailResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandStatusChangeRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandWithProductRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandWithProductResponse;
import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.brand.repository.BrandRepository;
import com.ssu.shop.domain.order.repository.ShipmentRepository;
import com.ssu.shop.domain.product.dto.ProductSort;
import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;
    private final SellerService sellerService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ShipmentRepository shipmentRepository;
    private final Clock clock;

    /** 브랜드 등록 → PENDING. 관리자가 승인해야 이 브랜드로 상품을 등록할 수 있다. */
    @Transactional
    public BrandResponse create(Long userId, BrandCreateRequest request) {
        Seller seller = sellerService.getSeller(userId);
        return BrandResponse.from(save(seller, request));
    }

    /**
     * 브랜드 + 첫 상품 한 번에 등록 (V8, 팀 결정 2026-11-20). 한 트랜잭션이라 상품 등록이 실패하면(품번·옵션 오류 등)
     * 브랜드도 남지 않는다. 브랜드·상품 모두 승인 대기로 생기고, 관리자는 브랜드를 승인한 뒤 상품을 따로 승인한다.
     * (전에는 프론트가 첫 상품을 브라우저에 임시 저장했다가 승인 뒤 등록했다 → 다른 기기에서 누락, 판매자 토큰 저장 문제)
     */
    @Transactional
    public BrandWithProductResponse createWithProduct(Long userId, BrandWithProductRequest request) {
        Seller seller = sellerService.getSeller(userId);
        Brand brand = save(seller, request.brand());
        return new BrandWithProductResponse(BrandResponse.from(brand),
                productService.createInitial(brand, request.product().toCreateRequest(brand.getId())));
    }

    private Brand save(Seller seller, BrandCreateRequest request) {
        String name = request.name().trim();
        if (brandRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_BRAND_NAME);
        }
        Brand brand = Brand.create(seller, name, request.description(), request.logoUrl());
        try {
            brandRepository.saveAndFlush(brand);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_BRAND_NAME);
        }
        return brand;
    }

    /** 판매자 센터 내 브랜드 (삭제한 브랜드 제외) */
    public List<BrandResponse> myBrands(Long userId) {
        Seller seller = sellerService.getSeller(userId);
        return brandRepository.findBySeller_IdAndStatusNotOrderByIdDesc(seller.getId(), BrandStatus.DELETED).stream()
                .map(BrandResponse::from)
                .toList();
    }

    /** 브랜드 페이지: 승인된 브랜드만, 승인된 상품만 */
    public BrandDetailResponse publicDetail(Long brandId, int page, int size) {
        Brand brand = brandRepository.findById(brandId)
                .filter(Brand::isApproved)
                .orElseThrow(() -> BusinessException.notFound("브랜드"));
        return BrandDetailResponse.of(brand,
                productService.searchPublic(null, brandId, null, ProductSort.OLDEST.getParam(), page, size));
    }

    /** 판매자 본인의 브랜드 삭제 (V8) */
    @Transactional
    public BrandResponse deleteMine(Long userId, Long brandId) {
        Seller seller = sellerService.getSeller(userId);
        Brand brand = lockActive(brandId);
        if (!brand.isOwnedBy(seller.getId())) {
            throw new BusinessException(ErrorCode.BRAND_NOT_OWNED);
        }
        return delete(brand);
    }

    // ===== 관리자 =====

    /** status 를 주지 않으면 삭제한 브랜드는 빠진다 (status=DELETED 로 따로 조회) */
    public PageResponse<AdminBrandResponse> search(BrandStatus status, Pageable pageable) {
        return PageResponse.of(brandRepository.searchWithSeller(status, pageable), AdminBrandResponse::from);
    }

    /**
     * 브랜드 승인 / 거절. WHERE status = 'PENDING' 조건부 UPDATE 라서 관리자 두 명이 동시에 처리해도 한 명만 성공한다.
     */
    @Transactional
    public BrandResponse changeStatus(Long brandId, BrandStatusChangeRequest request) {
        BrandStatus to = request.status();
        if (to != BrandStatus.APPROVED && to != BrandStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "APPROVED 또는 REJECTED 로만 변경할 수 있습니다. 삭제는 DELETE /api/admin/brands/{id}");
        }
        String rejectReason = to == BrandStatus.REJECTED ? request.rejectReason() : null;
        int updated = brandRepository.transitStatus(brandId, BrandStatus.PENDING, to, rejectReason);
        if (updated == 0) {
            if (!brandRepository.existsById(brandId)) {
                throw BusinessException.notFound("브랜드");
            }
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "대기(PENDING) 상태의 브랜드만 처리할 수 있습니다.");
        }
        return BrandResponse.from(brandRepository.findById(brandId).orElseThrow());
    }

    /** 관리자의 브랜드 삭제 (V8) */
    @Transactional
    public BrandResponse deleteByAdmin(Long brandId) {
        return delete(lockActive(brandId));
    }

    // ===== 삭제 공통 =====

    /** 브랜드 행을 잠그고(FOR UPDATE) 읽는다. 이미 삭제한 브랜드는 없는 것처럼 */
    private Brand lockActive(Long brandId) {
        return brandRepository.findByIdForUpdate(brandId)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> BusinessException.notFound("브랜드"));
    }

    /**
     * 브랜드 삭제 (V8, 팀 결정 2026-11-20). 판매자 탈퇴와 같은 방식이다:
     *  1) 소속 상품을 id 오름차순으로 한 행씩 판매 종료(DELETED) — 주문과 같은 순서로 상품 행을 잠가 데드락이 없다
     *  2) 그 브랜드 상품이 담긴 진행 중 주문(주문완료·배송준비·배송중)을 잠금 읽기로 센다 → 있으면 거절, 1) 까지 롤백
     *  3) 브랜드를 DELETED 로, 이름은 'deleted_{id}_원래이름' 으로 바꿔 같은 이름을 다시 쓸 수 있게 한다
     * 같은 브랜드에 상품을 등록하는 요청은 브랜드 행을 FOR SHARE 로 읽으므로 삭제가 끝날 때까지 기다렸다가 거절된다.
     */
    private BrandResponse delete(Brand brand) {
        for (Long productId : productRepository.findActiveIdsByBrand(brand.getId())) {
            productRepository.markDeleted(productId);
        }
        long inProgress = shipmentRepository.countInProgressByBrandForUpdate(brand.getId());
        if (inProgress > 0) {
            throw new BusinessException(ErrorCode.HAS_IN_PROGRESS_ORDERS,
                    "이 브랜드 상품의 진행 중인 주문(주문완료·배송준비·배송중)이 " + inProgress + "건 있어 삭제할 수 없습니다.");
        }
        brand.delete(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
        return BrandResponse.from(brand);
    }
}
