package com.readmate.global.security;

import com.readmate.domain.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * PK 조회는 클러스터드 인덱스 1회 접근이라 비용이 작으므로 정합성을 우선했다.
 *
 * @Component 로 등록하면 서블릿 필터로도 자동 등록되어 두 번 실행되므로 SecurityConfig 에서 직접 생성한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Long userId = jwtProvider.getUserId(token);
                userRepository.findById(userId)
                        .filter(user -> user.isActive())
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

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
