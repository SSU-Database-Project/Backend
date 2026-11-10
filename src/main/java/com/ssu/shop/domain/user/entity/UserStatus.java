package com.ssu.shop.domain.user.entity;

/**
 * ACTIVE    정상
 * SUSPENDED 관리자 정지 (해제하면 다시 사용 가능)
 * WITHDRAWN 탈퇴 (개인정보 삭제, 되돌릴 수 없음. 주문 기록은 유지)
 */
public enum UserStatus {
    ACTIVE, SUSPENDED, WITHDRAWN
}
