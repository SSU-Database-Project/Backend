package com.ssu.shop.domain.cart.repository;

import com.ssu.shop.domain.cart.entity.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * 내 장바구니 전체를 쿼리 1번으로: 항목 + 옵션 + 상품 + 브랜드 + 색상 + 사이즈 (모두 To-One 이라 한 번에 가져와도 줄 수가 늘지 않는다).
     * 담은 순서대로.
     */
    @Query("""
            select i from CartItem i
              join fetch i.option o
              join fetch o.product p
              join fetch p.brand
              join fetch o.color
              join fetch o.size
             where i.cart.user.id = :userId
             order by i.id
            """)
    List<CartItem> findAllWithDetailsByUserId(@Param("userId") Long userId);

    /**
     * 주문할 항목: 체크한 것만, 주문에 필요한 것을 쿼리 1번으로 (옵션 + 상품 + 브랜드 + 판매자 + 색상 + 사이즈).
     */
    @Query("""
            select i from CartItem i
              join fetch i.option o
              join fetch o.product p
              join fetch p.brand b
              join fetch b.seller
              join fetch o.color
              join fetch o.size
             where i.cart.user.id = :userId and i.selected = true
             order by i.id
            """)
    List<CartItem> findSelectedForOrder(@Param("userId") Long userId);

    /** 주문한 항목을 한 문장으로 지우고 지운 줄 수를 돌려준다 (그 사이 장바구니가 바뀌었는지 확인하는 데 쓴다) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItem i where i.id in :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);

    /** 내 장바구니의 항목만 찾는다 (남의 항목 id 를 넣으면 없는 것으로 나온다) */
    @Query("select i from CartItem i where i.id = :id and i.cart.user.id = :userId")
    Optional<CartItem> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 담기: 처음 담는 옵션이면 새 줄, 이미 담긴 옵션이면 수량만 더한다 (uk_cart_items_cart_option).
     * 한 문장이라 같은 옵션을 동시에 두 번 눌러도 줄이 둘로 나뉘거나 수량이 빠지지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO cart_items (cart_id, option_id, quantity)
            VALUES (:cartId, :optionId, :quantity)
            ON DUPLICATE KEY UPDATE quantity = quantity + :quantity
            """)
    void addOrIncrease(@Param("cartId") Long cartId, @Param("optionId") Long optionId,
                       @Param("quantity") int quantity);

    @Query("select i.quantity from CartItem i where i.cart.id = :cartId and i.option.id = :optionId")
    int findQuantity(@Param("cartId") Long cartId, @Param("optionId") Long optionId);
}
