package com.readmate.global.security;

import com.readmate.domain.user.entity.Role;
import com.readmate.domain.user.entity.User;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** SecurityContext 에 저장되는 인증 주체. 컨트롤러에서 @AuthenticationPrincipal LoginUser 로 받는다. */
public record LoginUser(Long id, String email, Role role) {

    public static LoginUser from(User user) {
        return new LoginUser(user.getId(), user.getEmail(), user.getRole());
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
}
