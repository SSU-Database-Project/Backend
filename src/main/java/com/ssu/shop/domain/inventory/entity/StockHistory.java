package com.ssu.shop.domain.inventory.entity;

import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** stock_history: 옵션 단위 재고 변동 이력. INSERT 만 하는 append-only 로그 */
@Entity
@Table(name = "stock_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHistory extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private ProductOption option;

    @Column(name = "change_qty", nullable = false)
    private Integer changeQty;

    /** 변동 후 재고 (도출 가능하지만 이력 화면 표시 및 옵션 재고와의 정합성 검사용으로 저장) */
    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StockType type;

    /** ORDER / CANCEL 일 때만 존재 (DB CHECK 로 강제) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Column(length = 255)
    private String memo;

    private StockHistory(ProductOption option, int changeQty, int quantityAfter, StockType type,
                         OrderItem orderItem, String memo) {
        this.option = option;
        this.changeQty = changeQty;
        this.quantityAfter = quantityAfter;
        this.type = type;
        this.orderItem = orderItem;
        this.memo = memo;
    }

    public static StockHistory inbound(ProductOption option, int quantity, int quantityAfter, String memo) {
        return new StockHistory(option, quantity, quantityAfter, StockType.IN, null, memo);
    }

    public static StockHistory adjust(ProductOption option, int changeQty, int quantityAfter, String memo) {
        return new StockHistory(option, changeQty, quantityAfter, StockType.ADJUST, null, memo);
    }

    /** [BE-2] 주문 차감: changeQty 는 음수로 기록 */
    public static StockHistory order(ProductOption option, int orderedQty, int quantityAfter, OrderItem orderItem) {
        return new StockHistory(option, -orderedQty, quantityAfter, StockType.ORDER, orderItem, null);
    }

    /** [BE-2] 주문 취소 복원 */
    public static StockHistory cancel(ProductOption option, int restoredQty, int quantityAfter, OrderItem orderItem) {
        return new StockHistory(option, restoredQty, quantityAfter, StockType.CANCEL, orderItem, null);
    }
}
