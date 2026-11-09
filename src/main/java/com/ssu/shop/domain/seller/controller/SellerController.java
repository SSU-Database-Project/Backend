package com.ssu.shop.domain.seller.controller;

import com.ssu.shop.domain.seller.dto.SellerDtos.SellerApplyRequest;
import com.ssu.shop.domain.seller.dto.SellerDtos.SellerResponse;
import com.ssu.shop.domain.seller.service.SellerService;
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

@Tag(name = "Seller", description = "판매자 신청 / 내 판매자 정보")
@RestController
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @Operation(summary = "판매자(사업자) 등록 신청 → PENDING", description = "USER 권한. 거절된 경우 재신청 가능")
    @PostMapping("/api/sellers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SellerResponse> apply(@AuthenticationPrincipal LoginUser loginUser,
                                             @Valid @RequestBody SellerApplyRequest request) {
        return ApiResponse.success(sellerService.apply(loginUser.id(), request));
    }

    @Operation(summary = "내 판매자 정보")
    @GetMapping("/api/seller/me")
    public ApiResponse<SellerResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(sellerService.getMine(loginUser.id()));
    }
}
