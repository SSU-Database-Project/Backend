package com.ssu.shop.global.security;

import com.ssu.shop.domain.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * HS256 Access Token 발급/검증.
 *  - sub = userId
 *  - sid = 로그인 식별값 (users.login_session_id 와 같아야 인증된다 → 동시 로그인 방지)
 *  - role 클레임은 FE 표시용 (권한 판단은 DB 의 최신 role 사용)
 */
@Component
public class JwtProvider {

    static final String SESSION_CLAIM = "sid";

    private final SecretKey key;
    private final long validitySeconds;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.validitySeconds = properties.accessTokenValiditySeconds();
    }

    public String createAccessToken(Long userId, Role role, String sessionId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .claim(SESSION_CLAIM, sessionId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validitySeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException 서명 불일치, 만료, 형식 오류 */
    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new TokenClaims(Long.valueOf(claims.getSubject()), claims.get(SESSION_CLAIM, String.class));
    }

    /** @throws JwtException 서명 불일치, 만료, 형식 오류 */
    public Long getUserId(String token) {
        return parse(token).userId();
    }

    public long getValiditySeconds() {
        return validitySeconds;
    }

    /** sessionId 는 V6 이전에 발급된 토큰이면 null */
    public record TokenClaims(Long userId, String sessionId) {
    }
}
