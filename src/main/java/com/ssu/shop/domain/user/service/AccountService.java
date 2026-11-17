package com.ssu.shop.domain.user.service;

import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.domain.user.dto.AccountDtos.PasswordChangeRequest;
import com.ssu.shop.domain.user.dto.AccountDtos.ProfileUpdateRequest;
import com.ssu.shop.domain.user.dto.AccountDtos.WithdrawalCheckResponse;
import com.ssu.shop.domain.user.dto.AuthDtos.MeResponse;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지: 내 정보 수정, 비밀번호 변경, 본인 탈퇴 (팀 결정 2026-11-17) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountLifecycle accountLifecycle;

    /** 모든 회원: 이름·전화번호. 판매자는 상호·대표자명도 (사업자번호는 고정) */
    @Transactional
    public MeResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = accountLifecycle.lock(userId);
        Seller seller = null;
        if (user.getRole() == Role.SELLER) {
            if (request.seller() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "판매자는 판매자 정보(seller)를 함께 보내야 합니다.");
            }
            seller = sellerRepository.findByUserId(userId).orElseThrow(() -> BusinessException.notFound("판매자 정보"));
            seller.updateInfo(request.seller().companyName().trim(), request.seller().representativeName().trim());
        } else if (request.seller() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "판매자 정보는 판매자만 수정할 수 있습니다.");
        }
        user.updateProfile(request.name().trim(), AuthService.normalizePhone(request.phone()));
        return MeResponse.of(user, seller);
    }

    /** 현재 비밀번호를 확인한 뒤 바꾼다. 모든 로그인이 끊긴다 → 다음 요청부터 401 LOGIN_EXPIRED, 새 비밀번호로 다시 로그인 */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = accountLifecycle.lock(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH, "현재 비밀번호가 올바르지 않습니다.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /** 탈퇴 팝업에 띄울 안내. 진행 중인 주문 = 주문완료·배송준비·배송중 */
    public WithdrawalCheckResponse withdrawalCheck(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("회원"));
        if (user.getRole() == Role.ADMIN) {
            return new WithdrawalCheckResponse(false, 0, "관리자 계정은 탈퇴할 수 없습니다.");
        }
        long count = accountLifecycle.countInProgress(user);
        if (user.getRole() == Role.SELLER) {
            return count > 0
                    ? new WithdrawalCheckResponse(false, count, "처리 중인 주문이 " + count
                            + "건 있어 탈퇴할 수 없습니다. 배송을 모두 처리한 뒤 탈퇴해 주세요.")
                    : new WithdrawalCheckResponse(true, 0, "탈퇴하면 등록한 상품이 모두 판매 종료됩니다. 정말 탈퇴하시겠습니까?");
        }
        return count > 0
                ? new WithdrawalCheckResponse(true, count, "진행 중인 주문이 " + count
                        + "건 존재합니다. 탈퇴해도 주문한 상품은 배송됩니다. 정말 탈퇴하시겠습니까?")
                : new WithdrawalCheckResponse(true, 0, "정말 탈퇴하시겠습니까?");
    }

    /**
     * 본인 탈퇴. 비밀번호를 다시 확인한다.
     * 구매자는 진행 중인 주문이 있어도 탈퇴(배송은 계속), 판매자는 진행 중인 주문이 있으면 409 HAS_IN_PROGRESS_ORDERS.
     */
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = accountLifecycle.lock(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_ADMIN, "관리자 계정은 탈퇴할 수 없습니다.");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        accountLifecycle.withdraw(user, true);
    }
}
