package com.ssu.shop.domain.user.service;

import com.ssu.shop.domain.order.repository.ShipmentRepository;
import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.entity.UserStatus;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 회원 정지·탈퇴 규칙. 관리자 처리(AdminUserService)와 본인 탈퇴(AccountService)가 같이 쓴다.
 *
 * 팀 결정 (2026-11-17)
 *  - "진행 중인 주문" = 배송 묶음이 주문완료·배송준비·배송중. 구매자는 내가 한 주문, 판매자는 내 상품에 들어온 주문.
 *  - 관리자: 진행 중인 주문이 있는 구매자·판매자는 정지·탈퇴시킬 수 없다 (정지 해제는 항상 가능).
 *  - 본인 탈퇴: 구매자는 진행 중인 주문이 있어도 확인 후 탈퇴할 수 있다 (배송은 판매자가 계속 진행).
 *              판매자는 진행 중인 주문이 있으면 탈퇴할 수 없다 (탈퇴하면 배송을 처리할 사람이 없다).
 *  - 정지된 판매자의 상품은 주문할 수 없다 (OrderService.create).
 *
 * 동시에 들어온 주문을 놓치지 않는 방법
 *  - 주문은 맨 처음 구매자·판매자 회원 행을 FOR SHARE 로 잠그고 둘 다 ACTIVE 인지 본다 (OrderService.lockAccounts).
 *  - 여기서는 대상 회원 행을 FOR UPDATE 로 먼저 잠근다 (findByIdForUpdate).
 *  → 주문이 먼저 잠갔으면 그 주문이 커밋될 때까지 기다렸다가 아래 잠금 읽기로 그 주문까지 센다.
 *    여기가 먼저 잠갔으면 주문이 기다렸다가 바뀐 상태(정지·탈퇴)를 보고 거절된다.
 *  - 진행 중 주문 수는 잠금 읽기(FOR SHARE)로 센다. 일반 SELECT 는 트랜잭션 처음의 스냅숏을 읽어,
 *    회원 행을 기다리는 사이 커밋된 주문을 못 볼 수 있다.
 *  잠그는 순서는 주문과 같다: 회원 → 상품. 그래서 서로 기다리다 멈추지(데드락) 않는다.
 */
@Component
@RequiredArgsConstructor
public class AccountLifecycle {

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final ShipmentRepository shipmentRepository;
    private final PasswordEncoder passwordEncoder;

    /** 회원 행을 잠그고 읽는다. 트랜잭션에서 회원을 처음 읽는 곳이어야 최신 값이다 */
    public User lock(Long userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(() -> BusinessException.notFound("회원"));
    }

    /** 팝업 안내용 (잠그지 않음). 관리자는 주문이 없으므로 0 */
    public long countInProgress(User user) {
        return switch (user.getRole()) {
            case USER -> shipmentRepository.countInProgressByBuyer(user.getId());
            case SELLER -> shipmentRepository.countInProgressBySeller(sellerOf(user).getId());
            case ADMIN -> 0;
        };
    }

    /** 관리자의 정지. lockedUser 는 lock() 으로 읽은 회원 */
    public void suspend(User lockedUser) {
        Seller seller = lockedUser.getRole() == Role.SELLER ? sellerOf(lockedUser) : null;
        rejectIfInProgress(lockedUser, seller, "정지");
        lockedUser.changeStatus(UserStatus.SUSPENDED);
    }

    /**
     * 탈퇴. lockedUser 는 lock() 으로 읽은 회원.
     * buyerMayHaveInProgress: 본인 탈퇴하는 구매자만 true (진행 중인 주문이 있어도 탈퇴).
     *
     * 판매자는 상품을 먼저 판매 종료(상품 행 잠금)한 뒤 진행 중 주문을 센다. 그 순간 주문 중이던 요청이 있으면
     * 상품 잠금에서 그 주문이 끝나기를 기다렸다가 센다. 주문이 있으면 예외 → 판매 종료까지 전부 롤백된다.
     */
    public void withdraw(User lockedUser, boolean buyerMayHaveInProgress) {
        if (lockedUser.getRole() == Role.SELLER) {
            Seller seller = sellerOf(lockedUser);
            stopSellingAll(seller.getId());
            rejectIfInProgress(lockedUser, seller, "탈퇴");
        } else if (!buyerMayHaveInProgress) {
            rejectIfInProgress(lockedUser, null, "탈퇴");
        }
        lockedUser.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()));
    }

    private void rejectIfInProgress(User user, Seller sellerOrNull, String action) {
        long count = sellerOrNull != null
                ? shipmentRepository.countInProgressBySellerForUpdate(sellerOrNull.getId())
                : shipmentRepository.countInProgressByBuyerForUpdate(user.getId());
        if (count > 0) {
            throw new BusinessException(ErrorCode.HAS_IN_PROGRESS_ORDERS,
                    "진행 중인 주문(주문완료·배송준비·배송중)이 " + count + "건 있어 " + action + "할 수 없습니다.");
        }
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

    private Seller sellerOf(User user) {
        return sellerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "판매자 정보가 없는 판매자 계정입니다."));
    }
}
