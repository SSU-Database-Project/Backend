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
            sellerRepository.findByUserId(userId).ifPresent(seller -> stopSellingAll(seller.getId()));
        }
        user.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()));
        return AdminUserResponse.from(user);
    }

    /**
     * 판매자의 상품을 id 오름차순으로 한 행씩 판매 종료한다.
     *
     * 주문(OrderService.create)도 상품 행을 id 오름차순으로 잠근다. 여기서 "브랜드에 속한 상품 전체"를 한 문장으로 바꾸면
     * MySQL 이 실행 계획이 정한 순서(예: 브랜드 인덱스 순서)로 잠가, 같은 판매자의 상품을 2개 이상 담은 주문과 서로를
     * 기다리다 멈출(데드락) 수 있다. 모두가 같은 순서로 잠그면 한쪽이 잠시 기다릴 뿐 멈추지 않는다.
     * 한 행씩 PK 로 바꾸는 이유는 StockService.lockOptions 와 같다: 그 행 하나만 잠근다.
     *
     * 판매자가 바로 이 순간 새로 등록해 아직 커밋하지 않은 상품은 목록에 없을 수 있다.
     * 탈퇴 직후 그 계정은 로그인할 수 없고, 남은 상품은 관리자가 차단할 수 있다.
     */
    private void stopSellingAll(Long sellerId) {
        for (Long productId : productRepository.findActiveIdsBySeller(sellerId)) {
            productRepository.markDeleted(productId);
        }
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
