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

    public PageResponse<AdminUserResponse> search(Role role, String keyword, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return PageResponse.of(userRepository.search(role, kw, pageable), AdminUserResponse::from);
    }

    /**
     * 회원 정지/해제. 물리 삭제 대신 상태값을 쓴다 — 주문 이력이 users 를 참조하기 때문(FK RESTRICT).
     * 정지 즉시 JWT 필터가 DB 의 status 를 확인하므로 기존 토큰도 바로 무효화된다.
     */
    @Transactional
    public AdminUserResponse changeStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("회원"));
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }
        user.changeStatus(status);
        return AdminUserResponse.from(user);
    }
}
