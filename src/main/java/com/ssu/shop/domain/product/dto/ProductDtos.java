package com.ssu.shop.domain.product.dto;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.product.entity.Color;
import com.ssu.shop.domain.product.entity.Gender;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.entity.Size;
import com.ssu.shop.domain.product.entity.SizeGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;

public final class ProductDtos {

    private ProductDtos() {
    }

    private static final String SEASON_REGEXP = "^[0-9]{2}(SS|FW)$";
    private static final String SEASON_MESSAGE = "시즌은 24SS, 24FW 형식이어야 합니다.";

    /** 옵션 1개 = 색상 × 사이즈 조합 + 초기 재고. 색상·사이즈는 /api/colors, /api/sizes 목록의 id */
    public record OptionRequest(
            @NotNull Long colorId,
            @NotNull Long sizeId,
            @NotNull @PositiveOrZero @Max(1_000_000) Integer stock) {
    }

    /**
     * 상품 등록 → PENDING (관리자 승인 후 구매자에게 보임).
     * brandId 는 본인의 승인된 브랜드, categoryId 는 소분류. imageUrl 은 이미지 업로드 API 가 돌려준 경로.
     */
    public record ProductCreateRequest(
            @NotNull Long brandId,
            @NotNull Long categoryId,
            @NotBlank @jakarta.validation.constraints.Size(max = 50) String productCode,
            @NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.Size(max = 5000) String description,
            @NotNull @PositiveOrZero @Max(100_000_000) Integer price,
            @NotNull Gender gender,
            @NotBlank @Pattern(regexp = SEASON_REGEXP, message = SEASON_MESSAGE) String season,
            @NotNull @Min(0) @Max(30) Integer shipDays,
            @jakarta.validation.constraints.Size(max = 500) String imageUrl,
            @NotEmpty @Valid java.util.List<OptionRequest> options) {
    }

    /**
     * 부분 수정(PATCH): null 인 필드는 변경하지 않는다. 하나라도 바뀌면 재승인(PENDING)으로 돌아가 승인 전까지 숨겨진다.
     * addOptions: 새 색상·사이즈 조합 추가. 기존 옵션의 재고는 여기서 바꾸지 않는다 → 입고/조정 API(BE-2, 승인 불필요).
     * 브랜드는 바꿀 수 없다(상품의 판매자가 바뀌면 주문 기록과 어긋난다).
     */
    public record ProductUpdateRequest(
            Long categoryId,
            @jakarta.validation.constraints.Size(min = 1, max = 50) String productCode,
            @jakarta.validation.constraints.Size(min = 1, max = 200) String name,
            @jakarta.validation.constraints.Size(max = 5000) String description,
            @PositiveOrZero @Max(100_000_000) Integer price,
            Gender gender,
            @Pattern(regexp = SEASON_REGEXP, message = SEASON_MESSAGE) String season,
            @Min(0) @Max(30) Integer shipDays,
            @jakarta.validation.constraints.Size(max = 500) String imageUrl,
            @Valid java.util.List<OptionRequest> addOptions) {
    }

    /** 판매자 판매 상태 토글: 승인된 상품만, ON_SALE ↔ SOLD_OUT */
    public record SaleStatusRequest(@NotNull ProductStatus status) {
    }

    /**
     * 관리자 처리. status:
     *  ON_SALE  = 승인(PENDING → ON_SALE) 또는 차단 해제(BLOCKED → ON_SALE)
     *  REJECTED = 거절(PENDING → REJECTED), rejectReason 함께
     *  BLOCKED  = 차단(ON_SALE / SOLD_OUT → BLOCKED)
     */
    public record AdminProductStatusRequest(
            @NotNull ProductStatus status,
            @jakarta.validation.constraints.Size(max = 255) String rejectReason) {
    }

    public record ColorResponse(Long id, String name, String hexCode) {

        public static ColorResponse from(Color c) {
            return new ColorResponse(c.getId(), c.getName(), c.getHexCode());
        }
    }

    public record SizeResponse(Long id, SizeGroup sizeGroup, String label) {

        public static SizeResponse from(Size s) {
            return new SizeResponse(s.getId(), s.getSizeGroup(), s.getLabel());
        }
    }

    public record OptionResponse(Long optionId, Long colorId, String colorName, String hexCode,
                                 Long sizeId, String sizeLabel, int stock, boolean soldOut) {

        public static OptionResponse from(ProductOption o) {
            return new OptionResponse(o.getId(), o.getColor().getId(), o.getColor().getName(),
                    o.getColor().getHexCode(), o.getSize().getId(), o.getSize().getLabel(),
                    o.getStockQuantity(), o.getStockQuantity() <= 0);
        }
    }

    public record ProductSummaryResponse(
            Long id,
            String productCode,
            String name,
            int price,
            String imageUrl,
            ProductStatus status,
            boolean soldOut,
            int totalStock,
            Long brandId,
            String brandName,
            Long categoryId,
            String categoryName,
            Gender gender,
            String season,
            LocalDateTime createdAt) {

        public static ProductSummaryResponse of(Product p, int totalStock) {
            Brand brand = p.getBrand();
            return new ProductSummaryResponse(p.getId(), p.getProductCode(), p.getName(), p.getPrice(),
                    p.getImageUrl(), p.getStatus(), p.isSoldOut(totalStock), totalStock,
                    brand.getId(), brand.getName(), p.getCategory().getId(), p.getCategory().getName(),
                    p.getGender(), p.getSeason(), p.getCreatedAt());
        }
    }

    /** 상품 상세: 상품번호(품번), 성별, 시즌, 카테고리, 출고 예정, 재고, 색상·사이즈 옵션 */
    public record ProductDetailResponse(
            Long id,
            String productCode,
            String name,
            String description,
            int price,
            String imageUrl,
            Gender gender,
            String season,
            int shipDays,
            ProductStatus status,
            String rejectReason,
            boolean soldOut,
            int totalStock,
            Long brandId,
            String brandName,
            Long sellerId,
            String sellerCompanyName,
            Long categoryId,
            String categoryName,
            SizeGroup sizeGroup,
            java.util.List<OptionResponse> options,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public static ProductDetailResponse of(Product p, java.util.List<ProductOption> options) {
            int totalStock = options.stream().mapToInt(ProductOption::getStockQuantity).sum();
            Brand brand = p.getBrand();
            return new ProductDetailResponse(p.getId(), p.getProductCode(), p.getName(), p.getDescription(),
                    p.getPrice(), p.getImageUrl(), p.getGender(), p.getSeason(), p.getShipDays(), p.getStatus(),
                    p.getRejectReason(), p.isSoldOut(totalStock), totalStock, brand.getId(), brand.getName(),
                    brand.getSeller().getId(), brand.getSeller().getCompanyName(),
                    p.getCategory().getId(), p.getCategory().getName(), p.getCategory().getSizeGroup(),
                    options.stream().map(OptionResponse::from).toList(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
