package com.ssu.shop.domain.cart.repository;

import com.ssu.shop.domain.cart.entity.CartItem;
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

    /**
     * 주문한 항목 하나를, 주문할 때 읽은 그대로(수량이 같고 아직 체크된 상태)일 때만 지운다. 지운 줄 수(0 또는 1)를 돌려준다.
     * DELETE 는 트랜잭션 시작 시점이 아니라 지금 커밋된 값을 보므로, 주문하는 사이 다른 요청이 수량이나 체크를 바꿨거나
     * 먼저 주문해 지웠다면 0 이 나온다 → 주문 서비스가 DATA_CONFLICT 로 전체를 롤백한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from CartItem i where i.id = :id and i.quantity = :quantity and i.selected = true")
    int deleteIfUnchanged(@Param("id") Long id, @Param("quantity") int quantity);

    /** 내 장바구니의 항목만 찾는다 (남의 항목 id 를 넣으면 없는 것으로 나온다) */
    @Query("select i from CartItem i where i.id = :id and i.cart.user.id = :userId")
    Optional<CartItem> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 담기: 처음 담는 옵션이면 새 줄, 이미 담긴 옵션이면 수량을 더하고 다시 체크한다 (uk_cart_items_cart_option).
     * 한 문장이라 같은 옵션을 동시에 두 번 눌러도 줄이 둘로 나뉘거나 수량이 빠지지 않는다.
     * 다시 체크하는 이유: 체크를 풀어 둔 옵션을 다시 담았는데 풀린 채로 두면, 방금 담은 상품이 합계와 주문에서 빠진다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO cart_items (cart_id, option_id, quantity)
            VALUES (:cartId, :optionId, :quantity)
            ON DUPLICATE KEY UPDATE quantity = quantity + :quantity, selected = TRUE
            """)
    void addOrIncrease(@Param("cartId") Long cartId, @Param("optionId") Long optionId,
                       @Param("quantity") int quantity);

    @Query("select i.quantity from CartItem i where i.cart.id = :cartId and i.option.id = :optionId")
    int findQuantity(@Param("cartId") Long cartId, @Param("optionId") Long optionId);
}
