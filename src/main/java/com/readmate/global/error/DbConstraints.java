package com.readmate.global.error;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * DB 제약 위반 예외에서 "어떤 제약"이 위반됐는지 판별한다.
 * MySQL 메시지 예: Duplicate entry '1234567890' for key 'sellers.uk_sellers_business_number'
 * DDL 에서 제약 이름을 명시(uk_/fk_/ck_)해 둔 이유 중 하나다.
 */
public final class DbConstraints {

    private DbConstraints() {
    }

    public static boolean isViolated(DataIntegrityViolationException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
