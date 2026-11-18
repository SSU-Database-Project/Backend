package com.ssu.shop.domain.user.controller;

import com.ssu.shop.domain.user.dto.AccountDtos.PasswordChangeRequest;
import com.ssu.shop.domain.user.dto.AccountDtos.ProfileUpdateRequest;
import com.ssu.shop.domain.user.dto.AccountDtos.WithdrawRequest;
import com.ssu.shop.domain.user.dto.AccountDtos.WithdrawalCheckResponse;
import com.ssu.shop.domain.user.dto.AuthDtos.MeResponse;
import com.ssu.shop.domain.user.service.AccountService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 마이페이지. 계정 정보 보기는 GET /api/users/me (AuthController) */
@Tag(name = "My Page", description = "내 정보 수정 / 비밀번호 변경 / 회원 탈퇴 (모든 회원)")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "내 정보 수정",
            description = "모든 회원: name, phone(비우면 삭제). 판매자는 seller(companyName, representativeName) 필수. "
                    + "사업자번호는 바꿀 수 없다")
    @PatchMapping
    public ApiResponse<MeResponse> updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                                 @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success(accountService.updateProfile(loginUser.id(), request));
    }

    @Operation(summary = "비밀번호 변경",
            description = "현재 비밀번호가 틀리면 400 PASSWORD_MISMATCH. 바꾸면 지금 토큰도 끊긴다 "
                    + "(다음 요청부터 401 LOGIN_EXPIRED) → 새 비밀번호로 다시 로그인")
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        accountService.changePassword(loginUser.id(), request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "탈퇴 전 확인 (팝업 안내)",
            description = "진행 중인 주문(주문완료·배송준비·배송중) 수와 팝업 문구. "
                    + "구매자는 주문이 있어도 canWithdraw=true(배송은 계속), 판매자는 주문이 있으면 false, 관리자는 항상 false")
    @GetMapping("/withdrawal")
    public ApiResponse<WithdrawalCheckResponse> withdrawalCheck(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(accountService.withdrawalCheck(loginUser.id()));
    }

    @Operation(summary = "회원 탈퇴 (본인)",
            description = "비밀번호를 다시 입력받는다(틀리면 400 PASSWORD_MISMATCH). 판매자는 진행 중인 주문이 있으면 "
                    + "409 HAS_IN_PROGRESS_ORDERS, 탈퇴하면 상품이 모두 판매 종료. 관리자는 403 CANNOT_MODIFY_ADMIN. "
                    + "주문 기록은 남는다")
    @PostMapping("/withdrawal")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal LoginUser loginUser,
                                      @Valid @RequestBody WithdrawRequest request) {
        accountService.withdraw(loginUser.id(), request.password());
        return ApiResponse.success(null);
    }
}
