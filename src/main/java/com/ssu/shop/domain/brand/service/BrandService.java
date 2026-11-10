package com.ssu.shop.domain.brand.service;

import com.ssu.shop.domain.brand.dto.BrandDtos.AdminBrandResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandCreateRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandDetailResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandStatusChangeRequest;
import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.brand.repository.BrandRepository;
import com.ssu.shop.domain.product.dto.ProductSort;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
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

    /** 브랜드 등록 → PENDING. 관리자가 승인해야 이 브랜드로 상품을 등록할 수 있다. */
    @Transactional
    public BrandResponse create(Long userId, BrandCreateRequest request) {
        Seller seller = sellerService.getSeller(userId);
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
        return BrandResponse.from(brand);
    }

    public List<BrandResponse> myBrands(Long userId) {
        Seller seller = sellerService.getSeller(userId);
        return brandRepository.findBySeller_IdOrderByIdDesc(seller.getId()).stream()
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

    // ===== 관리자 =====

    public PageResponse<AdminBrandResponse> search(BrandStatus status, Pageable pageable) {
        return PageResponse.of(brandRepository.searchWithSeller(status, pageable), AdminBrandResponse::from);
    }

    /**
     * 브랜드 승인 / 거절. WHERE status = 'PENDING' 조건부 UPDATE 라서 관리자 두 명이 동시에 처리해도 한 명만 성공한다.
     */
    @Transactional
    public BrandResponse changeStatus(Long brandId, BrandStatusChangeRequest request) {
        BrandStatus to = request.status();
        if (to == BrandStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "APPROVED 또는 REJECTED 로만 변경할 수 있습니다.");
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
}
