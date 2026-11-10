package com.ssu.shop.domain.seller.repository;

import com.ssu.shop.domain.seller.entity.Seller;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    /** uk_sellers_user 인덱스 사용 */
    Optional<Seller> findByUserId(Long userId);

    boolean existsByBusinessNumber(String businessNumber);
}
