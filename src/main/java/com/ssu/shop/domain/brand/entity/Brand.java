package com.ssu.shop.domain.brand.entity;

import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.global.common.BaseTimeEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * brands: 판매자가 등록하고 관리자가 승인한다. 승인된 브랜드에만 상품을 등록할 수 있다.
 * 모든 상품이 브랜드에 속하므로 "상품의 판매자"는 brands.seller_id 로 정해진다.
 */
@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, updatable = false)
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
    private BrandStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    /** 삭제 시각 (V8). DELETED 일 때만 값이 있다 (DB CHECK) */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Brand(Seller seller, String name, String description, String logoUrl) {
        this.seller = seller;
        this.name = name;
        this.description = description;
        this.logoUrl = logoUrl;
        this.status = BrandStatus.PENDING;
    }

    public static Brand create(Seller seller, String name, String description, String logoUrl) {
        return new Brand(seller, name, description, logoUrl);
    }

    /**
     * 삭제 (V8): 행은 남기고(주문 기록이 상품 → 브랜드를 참조) 상태만 DELETED.
     * 이름은 'deleted_{id}_원래이름' 으로 바꿔 다른 브랜드가 같은 이름을 다시 쓸 수 있게 한다 (uk_brands_name, 팀 결정).
     */
    public void delete(LocalDateTime now) {
        String renamed = "deleted_" + id + "_" + name;
        this.name = renamed.length() > 100 ? renamed.substring(0, 100) : renamed;
        this.status = BrandStatus.DELETED;
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return status == BrandStatus.DELETED;
    }

    public boolean isOwnedBy(Long sellerId) {
        return seller.getId().equals(sellerId);
    }

    public boolean isApproved() {
        return status == BrandStatus.APPROVED;
    }
}
