package com.ssu.shop.domain.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 마이페이지 (내 정보 수정 · 비밀번호 변경 · 본인 탈퇴) */
public final class AccountDtos {

    private AccountDtos() {
    }

    /**
     * PATCH /api/users/me — 화면의 수정 폼 값을 그대로 보낸다 (보낸 값으로 바뀜).
     * phone 을 비우거나(null·"") 보내지 않으면 번호를 지운다.
     * seller 는 판매자만, 판매자는 필수. 사업자번호는 바꿀 수 없다 (팀 결정).
     */
    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 50) String name,
            @Pattern(regexp = "^$|^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 아닙니다.") String phone,
            @Valid SellerProfileRequest seller) {
    }

    public record SellerProfileRequest(
            @NotBlank @Size(max = 100) String companyName,
            @NotBlank @Size(max = 50) String representativeName) {
    }

    /** PATCH /api/users/me/password — 바꾸면 지금 로그인도 끊긴다 → 새 비밀번호로 다시 로그인 */
    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword) {
    }

    /**
     * GET /api/users/me/withdrawal — 탈퇴 버튼을 누르면 먼저 호출해 팝업에 message 를 보여 준다.
     * canWithdraw 가 false 면 탈퇴 버튼 대신 안내만 (판매자의 진행 중 주문, 관리자 계정).
     */
    public record WithdrawalCheckResponse(boolean canWithdraw, long inProgressOrderCount, String message) {
    }

    /** POST /api/users/me/withdrawal — 비밀번호를 다시 입력받는다 (팀 결정) */
    public record WithdrawRequest(@NotBlank String password) {
    }
}
