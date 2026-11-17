package com.ssu.shop.domain.cart.repository;

import com.ssu.shop.domain.cart.entity.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /** 장바구니가 없으면 만든다. 이미 있으면 아무 일도 하지 않는다 (uk_carts_user 덕분에 동시에 눌러도 하나만 생긴다) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = "INSERT IGNORE INTO carts (user_id) VALUES (:userId)")
    void createIfAbsent(@Param("userId") Long userId);

    @Query("select c.id from Cart c where c.user.id = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);
}
