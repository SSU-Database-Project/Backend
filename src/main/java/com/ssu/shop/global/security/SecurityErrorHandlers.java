package com.ssu.shop.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 필터 단계의 인증/인가 실패는 @RestControllerAdvice 까지 오지 않으므로
 * 여기서 공통 응답 JSON 을 직접 작성한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint entryPoint() {
        // 토큰은 맞지만 다른 곳에서 새로 로그인했거나 비밀번호를 바꿔 끊긴 경우는 그 이유로 응답 (JwtAuthenticationFilter)
        return (request, response, ex) -> {
            Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
            write(response, reason instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED);
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code));
    }
}
