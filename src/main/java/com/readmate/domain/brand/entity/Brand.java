package com.readmate.domain.brand.entity;

import com.readmate.domain.seller.entity.Seller;
import com.readmate.domain.seller.entity.SellerStatus;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** brands: 승인 상태는 판매자와 같은 PENDING/APPROVED/REJECTED 를 사용 */
@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SellerStatus status;

    private Brand(Seller seller, String name, String description, String logoUrl) {
        this.seller = seller;
        this.name = name;
        this.description = description;
        this.logoUrl = logoUrl;
        this.status = SellerStatus.PENDING;
    }

    public static Brand create(Seller seller, String name, String description, String logoUrl) {
        return new Brand(seller, name, description, logoUrl);
    }

    public boolean isOwnedBy(Long sellerId) {
        return seller.getId().equals(sellerId);
    }

    public boolean isApproved() {
        return status == SellerStatus.APPROVED;
    }
}
