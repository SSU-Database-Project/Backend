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
 * products.
 * DB 에는 (brand_id, seller_id) → brands(id, seller_id) 복합 FK 가 있다.
 * JPA 는 brand, seller 를 각각 단일 @ManyToOne 으로 매핑하고, 두 값의 일치는 DB 제약이 최종 보장한다.
 *
 * 재고(inventory)는 의도적으로 역방향 매핑하지 않는다. 역방향 @OneToOne 은 Hibernate 에서 지연 로딩이 되지 않아
 * 상품을 읽을 때마다 재고 쿼리가 추가로 나가기 때문이다(N+1). 필요할 때 InventoryRepository 로 IN 조회한다.
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상품 소유자. 등록 후 변경 불가 (order_items.seller_id 스냅샷의 전제) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, updatable = false)
    private Seller seller;

    /** NULL = 브랜드 없는 독립 상품 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    private Product(Seller seller, Brand brand, Category category, String name, String description,
                    Integer price, String imageUrl) {
        this.seller = seller;
        this.brand = brand;
        this.category = category;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.status = ProductStatus.ON_SALE;
    }

    public static Product create(Seller seller, Brand brand, Category category, String name, String description,
                                 Integer price, String imageUrl) {
        return new Product(seller, brand, category, name, description, price, imageUrl);
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

    public void changeImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void changeCategory(Category category) {
        this.category = category;
    }

    /** null 이면 브랜드 해제 */
    public void changeBrand(Brand brand) {
        this.brand = brand;
    }

    public void changeStatus(ProductStatus status) {
        this.status = status;
    }

    public boolean isOwnedBy(Long sellerId) {
        return seller.getId().equals(sellerId);
    }

    public boolean isBlocked() {
        return status == ProductStatus.BLOCKED;
    }

    /** 품절 판정: 판매자 수동 품절 또는 재고 0 */
    public boolean isSoldOut(int stockQuantity) {
        return status == ProductStatus.SOLD_OUT || stockQuantity <= 0;
    }
}
