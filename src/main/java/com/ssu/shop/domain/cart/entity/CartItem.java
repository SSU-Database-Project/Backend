package com.ssu.shop.domain.cart.entity;

import com.ssu.shop.domain.product.entity.ProductOption;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * [BE-2 골격] cart_items: 옵션(색상×사이즈) 단위. UNIQUE(cart_id, option_id) → 같은 옵션을 다시 담으면 줄은 그대로, 수량 증가 + 다시 체크.
 * selected: 장바구니 체크 상태를 DB 에 저장해 다시 들어와도 유지한다(회의 결정). 합계는 selected 항목만.
 * 가격은 저장하지 않는다 — 장바구니는 항상 현재 가격으로 계산한다(주문의 스냅샷과 반대).
 *
 * @DynamicUpdate: 바뀐 컬럼만 UPDATE 한다. 없으면 체크만 바꿔도 읽어 둔 옛 수량까지 다시 써서,
 * 그 사이 다른 요청이 담기로 늘린 수량이 사라진다. (수량 변경끼리 겹치면 마지막 요청의 값이 남는데, 사용자가 정한 값이라 그대로 둔다.)
 */
@Entity
@Table(name = "cart_items")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private ProductOption option;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private boolean selected;

    private CartItem(Cart cart, ProductOption option, int quantity) {
        this.cart = cart;
        this.option = option;
        this.quantity = quantity;
        this.selected = true;
    }

    public static CartItem create(Cart cart, ProductOption option, int quantity) {
        return new CartItem(cart, option, quantity);
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void changeSelected(boolean selected) {
        this.selected = selected;
    }
}
