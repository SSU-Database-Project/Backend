package com.ssu.shop.domain.seller.dto;

import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.entity.SellerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class SellerDtos {

    private SellerDtos() {
    }

    /** 사업자번호는 123-45-67890 또는 1234567890 모두 허용, 저장은 숫자 10자리 */
    public record SellerApplyRequest(
            @NotBlank @Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$", message = "사업자번호 형식(000-00-00000)이 아닙니다.")
            String businessNumber,
            @NotBlank @Size(max = 100) String companyName,
            @NotBlank @Size(max = 50) String representativeName) {
    }

    /** 관리자 승인/거절: status 는 APPROVED 또는 REJECTED */
    public record SellerStatusChangeRequest(
            @NotNull SellerStatus status,
            @Size(max = 255) String rejectReason) {
    }

    public record SellerResponse(
            Long id,
            Long userId,
            String userEmail,
            String businessNumber,
            String companyName,
            String representativeName,
            SellerStatus status,
            String rejectReason,
            LocalDateTime approvedAt,
            LocalDateTime createdAt) {

        /** seller.user 를 읽으므로 트랜잭션 안에서 호출하거나 user 를 fetch join 해야 한다. */
        public static SellerResponse from(Seller s) {
            return new SellerResponse(s.getId(), s.getUser().getId(), s.getUser().getEmail(), s.getBusinessNumber(),
                    s.getCompanyName(), s.getRepresentativeName(), s.getStatus(), s.getRejectReason(),
                    s.getApprovedAt(), s.getCreatedAt());
        }
    }
}
