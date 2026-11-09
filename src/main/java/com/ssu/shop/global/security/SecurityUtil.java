package com.ssu.shop.global.security;

import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 컨트롤러 파라미터로 받기 어려운 곳(서비스 등)에서 현재 로그인 사용자 ID 조회. */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser.id();
    }
}
