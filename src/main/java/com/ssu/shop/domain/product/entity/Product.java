package com.ssu.shop.domain.product.entity;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.category.entity.Category;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * products. 모든 상품은 브랜드에 속하고(brand_id NOT NULL), 판매자는 brand.seller 로 정해진다.
 * 판매자를 따로 저장하지 않는 이유: brand_id → seller_id 이행 종속이라 중복 저장하면 3NF 위반이다(V4).
 *
 * 옵션(product_options)은 역방향 컬렉션으로 매핑하지 않는다. 목록 조회에서 상품마다 옵션을 읽는 N+1 을 피하고,
 * 필요할 때 ProductOptionRepository 로 IN 조회한다.
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 등록 후 브랜드 변경 불가 → 상품의 판매자도 바뀌지 않는다 (주문 기록의 판매자와 어긋나지 않음) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false, updatable = false)
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** 품번. 브랜드 안에서 유일 (uk_products_brand_code). 화면의 "상품번호" */
    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Gender gender;

    /** 출시 시즌: 24SS, 24FW (DB CHECK 로 형식 강제) */
    @Column(nullable = false, length = 4, columnDefinition = "char(4)")
    private String season;

    /** 주문 후 N일 내 출고 */
    @Column(name = "ship_days", nullable = false)
    private Integer shipDays;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    private Product(Brand brand, Category category, String productCode, String name, String description,
                    Integer price, Gender gender, String season, Integer shipDays, String imageUrl) {
        this.brand = brand;
        this.category = category;
        this.productCode = productCode;
        this.name = name;
        this.description = description;
        this.price = price;
        this.gender = gender;
        this.season = season;
        this.shipDays = shipDays;
        this.imageUrl = imageUrl;
        this.status = ProductStatus.PENDING;
    }

    /** 새 상품은 관리자 승인 전까지 PENDING (구매자에게 보이지 않음) */
    public static Product create(Brand brand, Category category, String productCode, String name,
                                 String description, Integer price, Gender gender, String season,
                                 Integer shipDays, String imageUrl) {
        return new Product(brand, category, productCode, name, description, price, gender, season, shipDays,
                imageUrl);
    }

    public Seller getSeller() {
        return brand.getSeller();
    }

    public boolean isOwnedBy(Long sellerId) {
        return brand.isOwnedBy(sellerId);
    }

    public void changeCategory(Category category) {
        this.category = category;
    }

    public void changeProductCode(String productCode) {
        this.productCode = productCode;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void changePrice(Integer price) {
        this.price = price;
    }

    public void changeGender(Gender gender) {
        this.gender = gender;
    }

    public void changeSeason(String season) {
        this.season = season;
    }

    public void changeShipDays(Integer shipDays) {
        this.shipDays = shipDays;
    }

    public void changeImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /** 정보가 바뀌면 다시 승인을 받는다. 승인될 때까지 구매자에게 숨긴다(회의 결정). */
    public void requestReapproval() {
        this.status = ProductStatus.PENDING;
        this.rejectReason = null;
    }

    public void changeSaleStatus(ProductStatus status) {
        this.status = status;
    }

    public void delete() {
        this.status = ProductStatus.DELETED;
    }

    public boolean isEditable() {
        return status != ProductStatus.BLOCKED && status != ProductStatus.DELETED;
    }

    /** 품절 판정: 판매자 수동 품절 또는 모든 옵션 재고 0 */
    public boolean isSoldOut(int totalStock) {
        return status == ProductStatus.SOLD_OUT || totalStock <= 0;
    }
}
