package com.ssu.shop.domain.user.controller;

import com.ssu.shop.domain.user.dto.AuthDtos.LoginRequest;
import com.ssu.shop.domain.user.dto.AuthDtos.MeResponse;
import com.ssu.shop.domain.user.dto.AuthDtos.SignupRequest;
import com.ssu.shop.domain.user.dto.AuthDtos.SignupResponse;
import com.ssu.shop.domain.user.dto.AuthDtos.TokenResponse;
import com.ssu.shop.domain.user.service.AuthService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입 / 로그인 / 내 정보")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일 중복 시 409 DUPLICATE_EMAIL")
    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(summary = "로그인 (JWT 발급)",
            description = "동시 로그인 방지: 새로 로그인하면 이전에 받은 토큰은 바로 무효 (그 토큰으로 요청하면 401 LOGGED_IN_ELSEWHERE)")
    @PostMapping("/api/auth/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "내 정보 (마이페이지 계정 확인)",
            description = "email(로그인 계정명)·이름·전화번호·role·status·가입일. 판매자면 seller(상호·대표자명·사업자번호)")
    @GetMapping("/api/users/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(authService.me(loginUser.id()));
    }
}
