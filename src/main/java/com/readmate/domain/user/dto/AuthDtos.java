package com.readmate.domain.user.dto;

import com.readmate.domain.seller.entity.Seller;
import com.readmate.domain.seller.entity.SellerStatus;
import com.readmate.domain.user.entity.Role;
import com.readmate.domain.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 50) String name,
            @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 아닙니다.") String phone) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record SignupResponse(Long id, String email) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {

        public static TokenResponse bearer(String token, long expiresIn, MeResponse user) {
            return new TokenResponse(token, "Bearer", expiresIn, user);
        }
    }

    /** GET /api/users/me */
    public record MeResponse(Long id, String email, String name, String phone, Role role, SellerInfo seller) {

        public static MeResponse of(User user, Seller sellerOrNull) {
            return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(), user.getRole(),
                    sellerOrNull == null ? null : SellerInfo.from(sellerOrNull));
        }
    }

    /** 판매자 신청 이력이 있으면 상태를 함께 내려 FE 가 "신청 대기중" 등을 표시할 수 있게 한다. */
    public record SellerInfo(Long sellerId, String companyName, SellerStatus status, String rejectReason) {

        public static SellerInfo from(Seller seller) {
            return new SellerInfo(seller.getId(), seller.getCompanyName(), seller.getStatus(), seller.getRejectReason());
        }
    }
}
