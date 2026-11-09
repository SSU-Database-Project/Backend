package com.readmate.domain.user.controller;

import com.readmate.domain.user.dto.AdminUserDtos.AdminUserResponse;
import com.readmate.domain.user.dto.AdminUserDtos.UserStatusChangeRequest;
import com.readmate.domain.user.entity.Role;
import com.readmate.domain.user.service.AdminUserService;
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

@Tag(name = "Admin - User", description = "회원 조회 / 정지")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "회원 목록", description = "keyword: 이메일 또는 이름 부분 일치")
    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(adminUserService.search(role, keyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @Operation(summary = "회원 정지 / 해제 (ACTIVE | SUSPENDED)")
    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminUserResponse> changeStatus(@PathVariable Long userId,
                                                       @Valid @RequestBody UserStatusChangeRequest request) {
        return ApiResponse.success(adminUserService.changeStatus(userId, request.status()));
    }
}
