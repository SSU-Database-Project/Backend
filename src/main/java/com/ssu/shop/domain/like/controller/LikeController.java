package com.ssu.shop.domain.like.controller;

import com.ssu.shop.domain.like.dto.LikeDtos.LikeStatusResponse;
import com.ssu.shop.domain.like.service.LikeService;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Like", description = "좋아요 (일반회원)")
@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "좋아요", description = "이미 눌렀으면 그대로 성공. 구매자에게 보이는 상품만")
    @PostMapping("/api/products/{productId}/like")
    public ApiResponse<LikeStatusResponse> like(@AuthenticationPrincipal LoginUser loginUser,
                                                @PathVariable Long productId) {
        return ApiResponse.success(likeService.like(loginUser.id(), productId));
    }

    @Operation(summary = "좋아요 취소", description = "누르지 않았어도 그대로 성공")
    @DeleteMapping("/api/products/{productId}/like")
    public ApiResponse<LikeStatusResponse> unlike(@AuthenticationPrincipal LoginUser loginUser,
                                                  @PathVariable Long productId) {
        return ApiResponse.success(likeService.unlike(loginUser.id(), productId));
    }

    @Operation(summary = "좋아요 상태 (하트 표시용)", description = "내가 눌렀는지 + 전체 좋아요 수")
    @GetMapping("/api/products/{productId}/like")
    public ApiResponse<LikeStatusResponse> status(@AuthenticationPrincipal LoginUser loginUser,
                                                  @PathVariable Long productId) {
        return ApiResponse.success(likeService.status(loginUser.id(), productId));
    }

    @Operation(summary = "내 좋아요 목록 (마이페이지)", description = "최근에 누른 순. 숨겨지거나 삭제된 상품은 빠진다")
    @GetMapping("/api/users/me/likes")
    public ApiResponse<PageResponse<ProductSummaryResponse>> myLikes(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(likeService.myLikes(loginUser.id(), page, size));
    }
}
