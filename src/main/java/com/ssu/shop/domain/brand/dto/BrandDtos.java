package com.ssu.shop.domain.brand.dto;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.global.common.PageResponse;
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
