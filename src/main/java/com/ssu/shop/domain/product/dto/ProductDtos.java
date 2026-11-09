package com.ssu.shop.domain.product.dto;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class ProductDtos {

    private ProductDtos() {
    }

    /** brandId 생략 = 브랜드 없는 상품. initialStock 으로 inventory 행을 함께 만든다. */
    public record ProductCreateRequest(
            @NotNull Long categoryId,
            Long brandId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            @NotNull @PositiveOrZero @Max(100_000_000) Integer price,
            @Size(max = 500) String imageUrl,
            @NotNull @PositiveOrZero @Max(1_000_000) Integer initialStock) {
    }

    /**
     * 부분 수정(PATCH): null 인 필드는 변경하지 않는다.
     * 브랜드 해제는 removeBrand = true (brandId: null 은 "변경 안 함"과 구분할 수 없으므로).
     * 재고 수량은 여기서 바꾸지 않는다 → 입고/조정 API(BE-2)로만 변경해 이력이 남게 한다.
     */
    public record ProductUpdateRequest(
            Long categoryId,
            Long brandId,
            Boolean removeBrand,
            @Size(min = 1, max = 200) String name,
            @Size(max = 5000) String description,
            @PositiveOrZero @Max(100_000_000) Integer price,
            @Size(max = 500) String imageUrl,
            ProductStatus status) {
    }

    public record ProductStatusChangeRequest(@NotNull ProductStatus status) {
    }

    public record ProductSummaryResponse(
            Long id,
            String name,
            int price,
            String imageUrl,
            ProductStatus status,
            boolean soldOut,
            int stockQuantity,
            Long brandId,
            String brandName,
            Long categoryId,
            String categoryName,
            LocalDateTime createdAt) {

        public static ProductSummaryResponse of(Product p, int stockQuantity) {
            Brand brand = p.getBrand();
            return new ProductSummaryResponse(p.getId(), p.getName(), p.getPrice(), p.getImageUrl(), p.getStatus(),
                    p.isSoldOut(stockQuantity), stockQuantity,
                    brand == null ? null : brand.getId(), brand == null ? null : brand.getName(),
                    p.getCategory().getId(), p.getCategory().getName(), p.getCreatedAt());
        }
    }

    public record ProductDetailResponse(
            Long id,
            String name,
            String description,
            int price,
            String imageUrl,
            ProductStatus status,
            boolean soldOut,
            int stockQuantity,
            Long sellerId,
            String sellerCompanyName,
            Long brandId,
            String brandName,
            Long categoryId,
            String categoryName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public static ProductDetailResponse of(Product p, int stockQuantity) {
            Brand brand = p.getBrand();
            return new ProductDetailResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getImageUrl(), p.getStatus(), p.isSoldOut(stockQuantity), stockQuantity,
                    p.getSeller().getId(), p.getSeller().getCompanyName(),
                    brand == null ? null : brand.getId(), brand == null ? null : brand.getName(),
                    p.getCategory().getId(), p.getCategory().getName(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
