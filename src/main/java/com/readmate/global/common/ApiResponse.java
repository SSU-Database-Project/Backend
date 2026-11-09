package com.readmate.global.common;

import com.readmate.global.error.ErrorCode;

/**
 * 공통 응답 포맷.
 * { "success": true,  "data": {...}, "error": null }
 * { "success": false, "data": null,  "error": { "code": "...", "message": "..." } }
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public record ErrorBody(String code, String message) {
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 응답 데이터가 없는 성공 (record 의 success() 접근자와 이름이 겹치지 않도록 ok) */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), message));
    }
}
