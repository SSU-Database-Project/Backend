package com.readmate.global.security;

import com.readmate.domain.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** HS256 Access Token 발급/검증. sub = userId, role 클레임은 FE 표시용(권한 판단은 DB의 최신 role 사용). */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long validitySeconds;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.validitySeconds = properties.accessTokenValiditySeconds();
    }

    public String createAccessToken(Long userId, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validitySeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException 서명 불일치, 만료, 형식 오류 */
    public Long getUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return Long.valueOf(claims.getSubject());
    }

    public long getValiditySeconds() {
        return validitySeconds;
    }
}
