package com.ssu.shop.domain.inventory.entity;

import com.ssu.shop.domain.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * inventory: products 와 1:1 식별 관계 (product_id = PK = FK, @MapsId).
 * 재고를 products 에서 분리해 주문 시 잠기는 행을 inventory 로 한정한다(계획서 4.3).
 * quantity >= 0 은 DB CHECK 가 최후 방어선으로 보장한다.
 *
 * [BE-2] 재고 차감/복원 로직과 동시성 처리(비관적 락 / @Version / 조건부 UPDATE)는 BE-2 담당.
 */
@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    /** 낙관적 락 사용 시 */
    @Version
    @Column(nullable = false)
    private Long version;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Inventory(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    /** 상품 등록 트랜잭션 안에서 함께 생성된다 → "모든 상품은 재고 행을 가진다" */
    public static Inventory create(Product product, int initialQuantity) {
        return new Inventory(product, initialQuantity);
    }
}
