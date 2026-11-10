package com.ssu.shop.domain.user.service;

import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.domain.user.dto.AdminUserDtos.AdminUserResponse;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.entity.UserStatus;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<AdminUserResponse> search(Role role, UserStatus status, String keyword, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return PageResponse.of(userRepository.search(role, status, kw, pageable), AdminUserResponse::from);
    }

    /**
     * 회원 정지 / 해제 (ACTIVE ↔ SUSPENDED).
     * JWT 필터가 요청마다 DB 의 status 를 확인하므로 정지하면 기존 토큰도 바로 막힌다.
     */
    @Transactional
    public AdminUserResponse changeStatus(Long userId, UserStatus status) {
        User user = getManageableUser(userId);
        if (status == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "탈퇴는 DELETE /api/admin/users/{id} 로 처리합니다.");
        }
        user.changeStatus(status);
        return AdminUserResponse.from(user);
    }

    /**
     * 탈퇴 처리 (회의의 "유저 삭제").
     * 행을 지우면 그 회원의 주문·판매 기록까지 지워야 하므로, 행은 남기고 개인정보만 지운다.
     * 판매자가 탈퇴하면 배송할 사람이 없으므로 그 판매자의 상품을 모두 판매 종료(DELETED)한다.
     */
    @Transactional
    public AdminUserResponse withdraw(Long userId) {
        User user = getManageableUser(userId);
        if (user.getRole() == Role.SELLER) {
            sellerRepository.findByUserId(userId)
                    .ifPresent(seller -> productRepository.deleteAllBySeller(seller.getId()));
        }
        user.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()));
        return AdminUserResponse.from(user);
    }

    private User getManageableUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("회원"));
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }
        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "이미 탈퇴한 회원입니다.");
        }
        return user;
    }
}
