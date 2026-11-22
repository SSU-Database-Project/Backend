package com.ssu.shop.global.security;

import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.global.security.JwtProvider.TokenClaims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {token} 검증 후 SecurityContext 에 LoginUser 저장.
 *
 * 토큰의 role 을 그대로 믿지 않고 매 요청 users 를 PK 로 조회한다.
 *  - 판매자 승인 직후 role 변경이 즉시 반영되고
 *  - 정지(SUSPENDED)된 회원은 토큰 만료 전이라도 바로 차단된다.
 *  - 토큰의 sid 가 users.login_session_id 와 다르면 차단한다 (동시 로그인 방지: 나중에 로그인한 쪽만 유효).
 * PK 조회는 클러스터드 인덱스 1회 접근이라 비용이 작으므로 정합성을 우선했다.
 *
 * 로그인 식별값 때문에 막힌 경우는 이유를 요청 속성에 남겨, EntryPoint 가 일반 401 대신
 * LOGGED_IN_ELSEWHERE / LOGIN_EXPIRED 로 응답한다 → FE 가 "다른 곳에서 로그인되었습니다" 팝업을 띄울 수 있다.
 *
 * @Component 로 등록하면 서블릿 필터로도 자동 등록되어 두 번 실행되므로 SecurityConfig 에서 직접 생성한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 인증하지 못한 이유 (ErrorCode). SecurityErrorHandlers 가 읽는다 */
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                TokenClaims claims = jwtProvider.parse(token);
                userRepository.findById(claims.userId())
                        .filter(User::isActive)
                        .filter(user -> isCurrentSession(user, claims, request))
                        .map(LoginUser::from)
                        .ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())));
            } catch (JwtException | IllegalArgumentException e) {
                // 잘못된/만료된 토큰: 인증하지 않은 상태로 진행 → 보호된 URL 이면 EntryPoint 가 401 응답
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isCurrentSession(User user, TokenClaims claims, HttpServletRequest request) {
        if (claims.sessionId() != null && Objects.equals(claims.sessionId(), user.getLoginSessionId())) {
            return true;
        }
        // DB 값이 있으면 그 뒤에 다른 곳에서 새로 로그인한 것, 없으면 비밀번호 변경 등으로 로그인이 모두 끊긴 것
        request.setAttribute(AUTH_ERROR_ATTRIBUTE,
                user.getLoginSessionId() != null ? ErrorCode.LOGGED_IN_ELSEWHERE : ErrorCode.LOGIN_EXPIRED);
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
