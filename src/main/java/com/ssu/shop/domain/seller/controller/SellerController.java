package com.ssu.shop.domain.seller.controller;

import com.ssu.shop.domain.seller.dto.SellerDtos.SellerResponse;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller", description = "내 판매자 정보 (판매자는 회원가입 때 선택)")
@RestController
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @Operation(summary = "내 판매자 정보")
    @GetMapping("/api/seller/me")
    public ApiResponse<SellerResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(sellerService.getMine(loginUser.id()));
    }
}
