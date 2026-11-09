package com.readmate.domain.seller.controller;

import com.readmate.domain.seller.dto.SellerDtos.SellerResponse;
import com.readmate.domain.seller.dto.SellerDtos.SellerStatusChangeRequest;
import com.readmate.domain.seller.entity.SellerStatus;
import com.readmate.domain.seller.service.SellerService;
import com.readmate.global.common.ApiResponse;
import com.readmate.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Seller", description = "판매자 승인/거절")
@RestController
@RequestMapping("/api/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    private final SellerService sellerService;

    @Operation(summary = "판매자 신청 목록", description = "status 생략 시 전체")
    @GetMapping
    public ApiResponse<PageResponse<SellerResponse>> list(
            @RequestParam(required = false) SellerStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(sellerService.search(status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @Operation(summary = "판매자 승인/거절", description = "승인 시 해당 회원 role → SELLER (같은 트랜잭션)")
    @PatchMapping("/{sellerId}/status")
    public ApiResponse<SellerResponse> changeStatus(@PathVariable Long sellerId,
                                                    @Valid @RequestBody SellerStatusChangeRequest request) {
        return ApiResponse.success(sellerService.changeStatus(sellerId, request));
    }
}
