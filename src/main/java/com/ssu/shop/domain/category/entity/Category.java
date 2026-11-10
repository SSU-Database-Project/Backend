package com.ssu.shop.domain.category.entity;

import com.ssu.shop.domain.product.entity.SizeGroup;
import com.ssu.shop.global.common.BaseCreatedEntity;
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
 * categories: parent_id 자기참조. NULL = 대분류, NOT NULL = 소분류 (2단계).
 * 패션 분류로 고정된 데이터다(V4 시드). 관리자 화면에서 바꾸지 않는다.
 * size_group 은 이 분류의 상품이 고를 수 있는 사이즈 목록(sizes.size_group)을 정한다.
 * children 컬렉션은 매핑하지 않는다 — 트리는 전체를 1번 조회한 뒤 메모리에서 조립(N+1 방지).
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "size_group", nullable = false, length = 20)
    private SizeGroup sizeGroup;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private Category(Category parent, String name, SizeGroup sizeGroup, int displayOrder) {
        this.parent = parent;
        this.name = name;
        this.sizeGroup = sizeGroup;
        this.displayOrder = displayOrder;
    }

    /** 테스트 데이터용. 운영 데이터는 마이그레이션 시드로만 넣는다. */
    public static Category create(Category parent, String name, SizeGroup sizeGroup, int displayOrder) {
        return new Category(parent, name, sizeGroup, displayOrder);
    }

    public boolean isRoot() {
        return parent == null;
    }

    /** 상품은 소분류(leaf)에만 등록할 수 있다. 2단계 구조이므로 부모가 있으면 leaf. */
    public boolean isLeaf() {
        return parent != null;
    }

    /** 지연 로딩 프록시라도 id 접근은 추가 쿼리를 발생시키지 않는다. */
    public Long getParentId() {
        return parent == null ? null : parent.getId();
    }
}
