package com.ssu.shop.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssu.shop.domain.user.entity.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private final JwtProvider provider =
            new JwtProvider(new JwtProperties("test-secret-key-0123456789-abcdefghijklmnop", 3600));

    @Test
    void 발급한_토큰에서_userId를_꺼낸다() {
        String token = provider.createAccessToken(42L, Role.SELLER, "sid-42");

        assertThat(provider.getUserId(token)).isEqualTo(42L);
        assertThat(provider.parse(token).sessionId()).isEqualTo("sid-42");
    }

    @Test
    void 다른_키로_서명된_토큰은_거부한다() {
        JwtProvider other = new JwtProvider(new JwtProperties("another-secret-key-0123456789-abcdefghijk", 3600));
        String forged = other.createAccessToken(1L, Role.ADMIN, "sid-1");

        assertThatThrownBy(() -> provider.getUserId(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰은_거부한다() {
        JwtProvider expired = new JwtProvider(new JwtProperties("test-secret-key-0123456789-abcdefghijklmnop", -10));
        String token = expired.createAccessToken(1L, Role.USER, "sid-1");

        assertThatThrownBy(() -> provider.getUserId(token)).isInstanceOf(JwtException.class);
    }
}
