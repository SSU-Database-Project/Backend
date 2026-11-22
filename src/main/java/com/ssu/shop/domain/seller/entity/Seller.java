package com.ssu.shop.domain.seller.entity;

import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * sellers: users 와 1:0..1 (user_id UNIQUE).
 * 가입할 때 "판매자"를 고르면 회원 행과 같은 트랜잭션에서 만들어진다. 승인 단계는 없다(V4).
 */
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

    private Seller(User user, String businessNumber, String companyName, String representativeName) {
        this.user = user;
        this.businessNumber = businessNumber;
        this.companyName = companyName;
        this.representativeName = representativeName;
    }

    public static Seller create(User user, String businessNumber, String companyName, String representativeName) {
        return new Seller(user, businessNumber, companyName, representativeName);
    }

    /** 마이페이지에서 상호·대표자명 수정. 사업자번호는 가입 때 값으로 고정한다 (팀 결정). */
    public void updateInfo(String companyName, String representativeName) {
        this.companyName = companyName;
        this.representativeName = representativeName;
    }
}
