package com.ssu.shop.domain.user.dto;

import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * 회원가입. role 은 USER(일반회원) 또는 SELLER(판매자). ADMIN 은 가입으로 만들 수 없다.
     * SELLER 면 사업자 정보(seller)가 필수이고, 가입과 동시에 판매자로 활동할 수 있다(승인 단계 없음).
     */
    public record SignupRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 50) String name,
            @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 아닙니다.") String phone,
            @NotNull @Schema(allowableValues = {"USER", "SELLER"}) Role role,
            @Valid SellerInfoRequest seller) {

        @AssertTrue(message = "가입 유형은 USER 또는 SELLER 만 가능합니다.")
        @Schema(hidden = true)
        public boolean isSignupRole() {
            return role == null || role != Role.ADMIN;
        }

        @AssertTrue(message = "판매자로 가입하려면 사업자 정보(seller)를 입력해야 합니다.")
        @Schema(hidden = true)
        public boolean isSellerInfoPresent() {
            return role != Role.SELLER || seller != null;
        }
    }

    /** 사업자번호는 123-45-67890 또는 1234567890 모두 허용, 저장은 숫자 10자리 */
    public record SellerInfoRequest(
            @NotBlank @Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$", message = "사업자번호 형식(000-00-00000)이 아닙니다.")
            String businessNumber,
            @NotBlank @Size(max = 100) String companyName,
            @NotBlank @Size(max = 50) String representativeName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record SignupResponse(Long id, String email, Role role) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {

        public static TokenResponse bearer(String token, long expiresIn, MeResponse user) {
            return new TokenResponse(token, "Bearer", expiresIn, user);
        }
    }

    /** GET /api/users/me — role 로 FE 가 화면(일반회원 / 판매자 / 관리자)을 나눈다 */
    public record MeResponse(Long id, String email, String name, String phone, Role role, SellerInfo seller) {

        public static MeResponse of(User user, Seller sellerOrNull) {
            return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(), user.getRole(),
                    sellerOrNull == null ? null : SellerInfo.from(sellerOrNull));
        }
    }

    public record SellerInfo(Long sellerId, String companyName, String businessNumber) {

        public static SellerInfo from(Seller seller) {
            return new SellerInfo(seller.getId(), seller.getCompanyName(), seller.getBusinessNumber());
        }
    }
}
