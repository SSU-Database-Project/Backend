package com.ssu.shop.domain.product.entity;

import com.ssu.shop.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * product_options: 상품의 색상 × 사이즈 조합. 재고는 옵션 단위로 센다.
 * UNIQUE(product_id, color_id, size_id) — 같은 조합은 한 번만. stock_quantity >= 0 은 DB CHECK 가 최후 방어선.
 *
 * [BE-2] 주문 차감/취소 복원과 동시성 처리(비관적 락 / @Version / 조건부 UPDATE)는 BE-2 담당.
 */
@Entity
@Table(name = "product_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id", nullable = false, updatable = false)
    private Color color;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "size_id", nullable = false, updatable = false)
    private Size size;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Version
    @Column(nullable = false)
    private Long version;

    private ProductOption(Product product, Color color, Size size, int stockQuantity) {
        this.product = product;
        this.color = color;
        this.size = size;
        this.stockQuantity = stockQuantity;
    }

    public static ProductOption create(Product product, Color color, Size size, int initialStock) {
        return new ProductOption(product, color, size, initialStock);
    }
}
