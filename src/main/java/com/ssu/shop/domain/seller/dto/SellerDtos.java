package com.ssu.shop.domain.seller.dto;

import com.ssu.shop.domain.seller.entity.Seller;
import java.time.LocalDateTime;

public final class SellerDtos {

    private SellerDtos() {
    }

    public record SellerResponse(
            Long id,
            Long userId,
            String userEmail,
            String businessNumber,
            String companyName,
            String representativeName,
            LocalDateTime createdAt) {

        /** seller.user 를 읽으므로 트랜잭션 안에서 호출하거나 user 를 fetch join 해야 한다. */
        public static SellerResponse from(Seller s) {
            return new SellerResponse(s.getId(), s.getUser().getId(), s.getUser().getEmail(), s.getBusinessNumber(),
                    s.getCompanyName(), s.getRepresentativeName(), s.getCreatedAt());
        }
    }
}
