package com.ssu.shop.domain.seller.service;

import com.ssu.shop.domain.seller.dto.SellerDtos.SellerApplyRequest;
import com.ssu.shop.domain.seller.dto.SellerDtos.SellerResponse;
import com.ssu.shop.domain.seller.dto.SellerDtos.SellerStatusChangeRequest;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.entity.SellerStatus;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.DbConstraints;
import com.ssu.shop.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerService {

    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;

    /**
     * 판매자 신청 (→ PENDING).
     * - 처음 신청: INSERT
     * - 거절 후 재신청: user_id UNIQUE 이므로 기존 행을 UPDATE (reapply)
     * - PENDING/APPROVED 상태에서 재신청: 409
     */
    @Transactional
    public SellerResponse apply(Long userId, SellerApplyRequest request) {
        String businessNumber = normalizeBusinessNumber(request.businessNumber());
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("회원"));

        sellerRepository.findByBusinessNumber(businessNumber)
                .filter(other -> !other.getUser().getId().equals(userId))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
                });

        Optional<Seller> existing = sellerRepository.findByUserId(userId);
        Seller seller;
        if (existing.isPresent()) {
            seller = existing.get();
            if (seller.getStatus() != SellerStatus.REJECTED) {
                throw new BusinessException(ErrorCode.ALREADY_SELLER_APPLIED);
            }
            seller.reapply(businessNumber, request.companyName().trim(), request.representativeName().trim());
        } else {
            seller = Seller.apply(user, businessNumber, request.companyName().trim(),
                    request.representativeName().trim());
        }

        try {
            sellerRepository.saveAndFlush(seller);
        } catch (DataIntegrityViolationException e) {
            // 위의 사전 확인과 INSERT 사이에 끼어든 동시 요청 → DB UNIQUE 가 막아준 경우
            if (DbConstraints.isViolated(e, "uk_sellers_business_number")) {
                throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
            }
            throw new BusinessException(ErrorCode.ALREADY_SELLER_APPLIED);
        }
        return SellerResponse.from(seller);
    }

    public SellerResponse getMine(Long userId) {
        return SellerResponse.from(getApprovedSeller(userId));
    }

    /** 판매자 기능 진입점에서 사용: 승인된 판매자만 통과 */
    public Seller getApprovedSeller(Long userId) {
        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_APPROVED));
        if (!seller.isApproved()) {
            throw new BusinessException(ErrorCode.SELLER_NOT_APPROVED);
        }
        return seller;
    }

    // ===== 관리자 =====

    public PageResponse<SellerResponse> search(SellerStatus status, Pageable pageable) {
        return PageResponse.of(sellerRepository.searchWithUser(status, pageable), SellerResponse::from);
    }

    /**
     * 판매자 승인/거절.
     * sellers.status 변경과 users.role 변경은 반드시 한 트랜잭션에서 함께 반영되어야 한다.
     * (users.role = SELLER 는 sellers.status = APPROVED 의 반정규화 값 — 계획서 4.2)
     */
    @Transactional
    public SellerResponse changeStatus(Long sellerId, SellerStatusChangeRequest request) {
        SellerStatus to = request.status();
        if (to == SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "APPROVED 또는 REJECTED 로만 변경할 수 있습니다.");
        }
        LocalDateTime approvedAt = to == SellerStatus.APPROVED ? LocalDateTime.now() : null;
        String rejectReason = to == SellerStatus.REJECTED ? request.rejectReason() : null;

        int updated = sellerRepository.transitStatus(sellerId, SellerStatus.PENDING, to, rejectReason, approvedAt);
        if (updated == 0) {
            if (!sellerRepository.existsById(sellerId)) {
                throw BusinessException.notFound("판매자");
            }
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "대기(PENDING) 상태의 신청만 처리할 수 있습니다.");
        }

        Seller seller = sellerRepository.findById(sellerId).orElseThrow();
        if (to == SellerStatus.APPROVED) {
            seller.getUser().promoteToSeller(); // 변경 감지 → 같은 트랜잭션에서 UPDATE users
        }
        return SellerResponse.from(seller);
    }

    static String normalizeBusinessNumber(String raw) {
        return raw.replace("-", "").trim();
    }
}
