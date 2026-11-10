package com.ssu.shop.domain.seller.service;

import com.ssu.shop.domain.seller.dto.SellerDtos.SellerResponse;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 정보 조회. 판매자 생성은 회원가입(AuthService.signup)에서 회원 행과 같은 트랜잭션으로 한다.
 * 판매자 승인 단계는 없다 — 관리자는 브랜드와 상품을 승인한다(회의 결정, V4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerService {

    private final SellerRepository sellerRepository;

    public SellerResponse getMine(Long userId) {
        return SellerResponse.from(getSeller(userId));
    }

    /** 판매자 기능 진입점에서 사용. /api/seller/** 는 SELLER 권한으로 이미 막혀 있지만 판매자 행을 찾아야 한다. */
    public Seller getSeller(Long userId) {
        return sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_REQUIRED));
    }

    public static String normalizeBusinessNumber(String raw) {
        return raw.replace("-", "").trim();
    }
}
