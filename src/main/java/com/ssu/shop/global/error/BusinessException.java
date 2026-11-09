package com.ssu.shop.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BusinessException notFound(String target) {
        return new BusinessException(ErrorCode.NOT_FOUND, target + "을(를) 찾을 수 없습니다.");
    }
}
