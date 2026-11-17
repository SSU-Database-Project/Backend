package com.ssu.shop.domain.order.entity;

import com.ssu.shop.domain.product.entity.ProductOption;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * [BE-2 골격] order_items: 주문 상품 줄 (옵션 단위). UNIQUE(shipment_id, option_id).
 * 주문·판매자는 shipment 로, 상품은 option 으로 도출된다(중복 저장 안 함).
 * 의도적 반정규화(스냅샷): product_name, color_name, size_label, unit_price — 주문 당시 값을 보존한다.
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private ProductOption option;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "color_name", nullable = false, length = 30)
    private String colorName;

    @Column(name = "size_label", nullable = false, length = 10)
    private String sizeLabel;

    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    private OrderItem(Shipment shipment, ProductOption option, String productName, String colorName,
                      String sizeLabel, int unitPrice, int quantity) {
        this.shipment = shipment;
        this.option = option;
        this.productName = productName;
        this.colorName = colorName;
        this.sizeLabel = sizeLabel;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    /** 주문 시점의 상품명·색상·사이즈·단가를 옵션에서 복사해 둔다. 만든 뒤에는 고치지 않는다. */
    public static OrderItem create(Shipment shipment, ProductOption option, int quantity) {
        return new OrderItem(shipment, option, option.getProduct().getName(), option.getColor().getName(),
                option.getSize().getLabel(), option.getProduct().getPrice(), quantity);
    }

    public long lineTotal() {
        return (long) unitPrice * quantity;
    }
}
