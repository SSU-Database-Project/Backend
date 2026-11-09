package com.readmate.domain.user.dto;

import com.readmate.domain.user.entity.Role;
import com.readmate.domain.user.entity.User;
import com.readmate.domain.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /** 비밀번호 해시는 절대 응답에 포함하지 않는다 */
    public record AdminUserResponse(Long id, String email, String name, String phone, Role role,
                                    UserStatus status, LocalDateTime createdAt) {

        public static AdminUserResponse from(User u) {
            return new AdminUserResponse(u.getId(), u.getEmail(), u.getName(), u.getPhone(), u.getRole(),
                    u.getStatus(), u.getCreatedAt());
        }
    }

    public record UserStatusChangeRequest(@NotNull UserStatus status) {
    }
}
