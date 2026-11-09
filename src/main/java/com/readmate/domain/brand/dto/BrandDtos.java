package com.readmate.domain.brand.dto;

import com.readmate.domain.brand.entity.Brand;
import com.readmate.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.readmate.domain.seller.entity.SellerStatus;
import com.readmate.global.common.PageResponse;
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

    public record BrandStatusChangeRequest(@NotNull SellerStatus status) {
    }

    public record BrandResponse(Long id, Long sellerId, String name, String description, String logoUrl,
                                SellerStatus status, LocalDateTime createdAt) {

        public static BrandResponse from(Brand b) {
            return new BrandResponse(b.getId(), b.getSeller().getId(), b.getName(), b.getDescription(),
                    b.getLogoUrl(), b.getStatus(), b.getCreatedAt());
        }
    }

    /** 관리자 목록: 신청한 판매자 회사명 포함 */
    public record AdminBrandResponse(Long id, Long sellerId, String sellerCompanyName, String name,
                                     String description, SellerStatus status, LocalDateTime createdAt) {

        public static AdminBrandResponse from(Brand b) {
            return new AdminBrandResponse(b.getId(), b.getSeller().getId(), b.getSeller().getCompanyName(),
                    b.getName(), b.getDescription(), b.getStatus(), b.getCreatedAt());
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
