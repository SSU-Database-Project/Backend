package com.readmate.domain.user.entity;

import com.readmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    private User(String email, String encodedPassword, String name, String phone, Role role) {
        this.email = email;
        this.password = encodedPassword;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    public static User create(String email, String encodedPassword, String name, String phone) {
        return new User(email, encodedPassword, name, phone, Role.USER);
    }

    public static User createAdmin(String email, String encodedPassword, String name) {
        return new User(email, encodedPassword, name, null, Role.ADMIN);
    }

    /** 판매자 승인 시 호출. sellers.status 변경과 같은 트랜잭션에서 실행되어야 한다. */
    public void promoteToSeller() {
        if (role == Role.USER) {
            this.role = Role.SELLER;
        }
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
