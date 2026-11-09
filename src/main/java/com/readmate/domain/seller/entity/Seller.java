package com.readmate.domain.seller.entity;

import com.readmate.domain.user.entity.User;
import com.readmate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** sellers: users 와 1:0..1 (user_id UNIQUE) */
@Entity
@Table(name = "sellers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 숫자 10자리, 하이픈 없이 저장 */
    @Column(name = "business_number", nullable = false, length = 10, columnDefinition = "char(10)")
    private String businessNumber;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "representative_name", nullable = false, length = 50)
    private String representativeName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SellerStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private Seller(User user, String businessNumber, String companyName, String representativeName) {
        this.user = user;
        this.businessNumber = businessNumber;
        this.companyName = companyName;
        this.representativeName = representativeName;
        this.status = SellerStatus.PENDING;
    }

    public static Seller apply(User user, String businessNumber, String companyName, String representativeName) {
        return new Seller(user, businessNumber, companyName, representativeName);
    }

    /**
     * 거절된 회원의 재신청. user_id 가 UNIQUE 이므로 새 행을 INSERT 하지 않고 기존 행을 갱신한다.
     */
    public void reapply(String businessNumber, String companyName, String representativeName) {
        this.businessNumber = businessNumber;
        this.companyName = companyName;
        this.representativeName = representativeName;
        this.status = SellerStatus.PENDING;
        this.rejectReason = null;
        this.approvedAt = null;
    }

    public boolean isApproved() {
        return status == SellerStatus.APPROVED;
    }
}
