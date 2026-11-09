package com.ssu.shop.domain.user.entity;

/** 서비스 관리자는 별도 테이블 없이 role 로 구분한다. */
public enum Role {
    USER, SELLER, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
