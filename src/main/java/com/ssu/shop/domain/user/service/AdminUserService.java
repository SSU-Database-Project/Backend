package com.ssu.shop.domain.user.service;

import com.ssu.shop.domain.user.dto.AdminUserDtos.AdminUserResponse;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.entity.UserStatus;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AccountLifecycle accountLifecycle;

    public PageResponse<AdminUserResponse> search(Role role, UserStatus status, String keyword, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return PageResponse.of(userRepository.search(role, status, kw, pageable), AdminUserResponse::from);
    }

    /**
     * 회원 정지 / 해제 (ACTIVE ↔ SUSPENDED).
     * JWT 필터가 요청마다 DB 의 status 를 확인하므로 정지하면 기존 토큰도 바로 막힌다.
     * 진행 중인 주문(주문완료·배송준비·배송중)이 있으면 정지할 수 없다 → 409 HAS_IN_PROGRESS_ORDERS (팀 결정).
     * 정지 해제는 항상 할 수 있다.
     */
    @Transactional
    public AdminUserResponse changeStatus(Long userId, UserStatus status) {
        if (status == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "탈퇴는 DELETE /api/admin/users/{id} 로 처리합니다.");
        }
        User user = getManageableUser(userId);
        if (status == UserStatus.SUSPENDED && user.getStatus() != UserStatus.SUSPENDED) {
            accountLifecycle.suspend(user);
        } else {
            user.changeStatus(status);
        }
        return AdminUserResponse.from(user);
    }

    /**
     * 탈퇴 처리 (회의의 "유저 삭제").
     * 행을 지우면 그 회원의 주문·판매 기록까지 지워야 하므로, 행은 남기고 개인정보만 지운다.
     * 판매자가 탈퇴하면 배송할 사람이 없으므로 그 판매자의 상품을 모두 판매 종료(DELETED)한다.
     * 진행 중인 주문이 있으면 탈퇴시킬 수 없다 → 409 HAS_IN_PROGRESS_ORDERS (팀 결정).
     */
    @Transactional
    public AdminUserResponse withdraw(Long userId) {
        User user = getManageableUser(userId);
        accountLifecycle.withdraw(user, false);
        return AdminUserResponse.from(user);
    }

    /** 대상 회원을 잠그고 읽는다 (이유는 AccountLifecycle 주석) */
    private User getManageableUser(Long userId) {
        User user = accountLifecycle.lock(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }
        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "이미 탈퇴한 회원입니다.");
        }
        return user;
    }
}
