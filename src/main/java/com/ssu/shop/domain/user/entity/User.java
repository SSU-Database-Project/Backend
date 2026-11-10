package com.ssu.shop.domain.user.entity;

import com.ssu.shop.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * users 테이블. (USER 는 PostgreSQL 예약어라 복수형 테이블명 사용)
 * 상태값은 MySQL ENUM 이 아닌 VARCHAR + CHECK 로 저장한다 → @JdbcTypeCode(VARCHAR)
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /** DB CHECK: status = WITHDRAWN 과 withdrawn_at IS NOT NULL 은 항상 함께 참/거짓 */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    private User(String email, String encodedPassword, String name, String phone, Role role) {
        this.email = email;
        this.password = encodedPassword;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    /** 가입 시 USER 또는 SELLER 를 고른다. ADMIN 은 가입으로 만들 수 없다(서비스에서 거부). */
    public static User create(String email, String encodedPassword, String name, String phone, Role role) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("관리자 계정은 가입으로 만들 수 없습니다.");
        }
        return new User(email, encodedPassword, name, phone, role);
    }

    public static User createAdmin(String email, String encodedPassword, String name) {
        return new User(email, encodedPassword, name, null, Role.ADMIN);
    }

    /** 정지 / 해제. 탈퇴는 withdraw() 로만 한다. */
    public void changeStatus(UserStatus status) {
        if (status == UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴는 withdraw() 로 처리합니다.");
        }
        this.status = status;
    }

    /**
     * 탈퇴 처리: 행은 남기고(주문 기록이 참조) 개인정보만 지운다.
     * 이메일은 UNIQUE 라 빈 값으로 둘 수 없어 id 기반 값으로 바꾼다 → 같은 이메일로 다시 가입할 수 있다.
     * 비밀번호는 어떤 입력과도 맞지 않는 값으로 바꿔 로그인을 막는다.
     */
    public void withdraw(String unusablePassword) {
        this.email = "withdrawn_" + id + "@withdrawn.local";
        this.password = unusablePassword;
        this.name = "탈퇴회원";
        this.phone = null;
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isWithdrawn() {
        return status == UserStatus.WITHDRAWN;
    }
}
