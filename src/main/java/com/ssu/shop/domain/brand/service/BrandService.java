package com.ssu.shop.domain.brand.service;

import com.ssu.shop.domain.brand.dto.BrandDtos.AdminBrandResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandCreateRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandDetailResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandResponse;
import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.repository.BrandRepository;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.entity.SellerStatus;
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

    /** 브랜드 등록 → PENDING (관리자 승인 후 상품에 지정 가능) */
    @Transactional
    public BrandResponse create(Long userId, BrandCreateRequest request) {
        Seller seller = sellerService.getApprovedSeller(userId);
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
        Seller seller = sellerService.getApprovedSeller(userId);
        return brandRepository.findBySeller_IdOrderByIdDesc(seller.getId()).stream()
                .map(BrandResponse::from)
                .toList();
    }

    /** 공개 브랜드 페이지: 승인된 브랜드만 노출 */
    public BrandDetailResponse publicDetail(Long brandId, int page, int size) {
        Brand brand = brandRepository.findById(brandId)
                .filter(Brand::isApproved)
                .orElseThrow(() -> BusinessException.notFound("브랜드"));
        return BrandDetailResponse.of(brand,
                productService.searchPublic(null, brandId, null, "latest", page, size));
    }

    // ===== 관리자 =====

    public PageResponse<AdminBrandResponse> search(SellerStatus status, Pageable pageable) {
        return PageResponse.of(brandRepository.searchWithSeller(status, pageable), AdminBrandResponse::from);
    }

    @Transactional
    public BrandResponse changeStatus(Long brandId, SellerStatus to) {
        if (to == SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "APPROVED 또는 REJECTED 로만 변경할 수 있습니다.");
        }
        int updated = brandRepository.transitStatus(brandId, SellerStatus.PENDING, to);
        if (updated == 0) {
            if (!brandRepository.existsById(brandId)) {
                throw BusinessException.notFound("브랜드");
            }
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "대기(PENDING) 상태의 브랜드만 처리할 수 있습니다.");
        }
        return BrandResponse.from(brandRepository.findById(brandId).orElseThrow());
    }
}
