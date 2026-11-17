package com.ssu.shop.domain.inventory.dto;

import com.ssu.shop.domain.inventory.entity.StockHistory;
import com.ssu.shop.domain.inventory.entity.StockType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class StockDtos {

    private StockDtos() {
    }

    /**
     * 입고·조정 요청.
     * IN(입고): quantity 는 1 이상. ADJUST(조정): 늘리면 양수, 줄이면 음수 (0 은 안 됨, 결과가 음수가 되는 조정은 거부).
     */
    public record StockChangeRequest(
            @NotNull @Schema(allowableValues = {"IN", "ADJUST"}) StockType type,
            @NotNull @Min(-1_000_000) @Max(1_000_000) Integer quantity,
            @Size(max = 255) String memo) {
    }

    /** 변경 후 옵션 재고 */
    public record StockResponse(Long optionId, int stockQuantity) {
    }

    /** 재고 이력 한 줄. orderItemId 는 주문(ORDER)·취소(CANCEL) 이력에만 있다 */
    public record StockHistoryResponse(Long id, StockType type, int changeQty, int quantityAfter, Long orderItemId,
                                       String memo, LocalDateTime createdAt) {

        public static StockHistoryResponse from(StockHistory h) {
            return new StockHistoryResponse(h.getId(), h.getType(), h.getChangeQty(), h.getQuantityAfter(),
                    h.getOrderItem() == null ? null : h.getOrderItem().getId(), h.getMemo(), h.getCreatedAt());
        }
    }
}
