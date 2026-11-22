package com.ssu.shop.domain.brand.dto;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.product.dto.ProductDtos.InitialProductRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductDetailResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.global.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class BrandDtos {

    private BrandDtos() {
    }

    public record BrandCreateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description,
            @Size(max = 500) String logoUrl) {
    }

    /**
     * 브랜드 + 첫 상품 한 번에 등록 (V8). 한 트랜잭션: 상품 등록이 실패하면 브랜드도 만들어지지 않는다.
     * 브랜드와 상품 모두 승인 대기(PENDING)로 생기고, 관리자는 브랜드를 먼저 승인한 뒤 상품을 따로 승인한다.
     */
    public record BrandWithProductRequest(
            @NotNull @Valid BrandCreateRequest brand,
            @NotNull @Valid InitialProductRequest product) {
    }

    public record BrandWithProductResponse(BrandResponse brand, ProductDetailResponse product) {
    }

    /** 관리자 승인/거절: status 는 APPROVED 또는 REJECTED, 거절이면 사유 */
    public record BrandStatusChangeRequest(
            @NotNull BrandStatus status,
            @Size(max = 255) String rejectReason) {
    }

    public record BrandResponse(Long id, Long sellerId, String name, String description, String logoUrl,
                                BrandStatus status, String rejectReason, LocalDateTime createdAt) {

        public static BrandResponse from(Brand b) {
            return new BrandResponse(b.getId(), b.getSeller().getId(), b.getName(), b.getDescription(),
                    b.getLogoUrl(), b.getStatus(), b.getRejectReason(), b.getCreatedAt());
        }
    }

    /** 관리자 목록: 신청한 판매자 회사명 포함 */
    public record AdminBrandResponse(Long id, Long sellerId, String sellerCompanyName, String name,
                                     String description, BrandStatus status, String rejectReason,
                                     LocalDateTime createdAt) {

        public static AdminBrandResponse from(Brand b) {
            return new AdminBrandResponse(b.getId(), b.getSeller().getId(), b.getSeller().getCompanyName(),
                    b.getName(), b.getDescription(), b.getStatus(), b.getRejectReason(), b.getCreatedAt());
        }
    }

    /** GET /api/brands/{id}: 브랜드 정보 + 소속 상품 페이지 */
    public record BrandDetailResponse(Long id, String name, String description, String logoUrl,
                                      String sellerCompanyName, PageResponse<ProductSummaryResponse> products) {

        public static BrandDetailResponse of(Brand b, PageResponse<ProductSummaryResponse> products) {
            return new BrandDetailResponse(b.getId(), b.getName(), b.getDescription(), b.getLogoUrl(),
                    b.getSeller().getCompanyName(), products);
        }
    }
}
