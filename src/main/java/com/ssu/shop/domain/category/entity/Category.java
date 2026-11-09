package com.ssu.shop.domain.category.entity;

import com.ssu.shop.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * categories: parent_id 자기참조. NULL = 대분류, NOT NULL = 소분류 (최대 2단계).
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

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private Category(Category parent, String name, int displayOrder) {
        this.parent = parent;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public static Category create(Category parent, String name, int displayOrder) {
        return new Category(parent, name, displayOrder);
    }

    public void update(String name, Integer displayOrder) {
        if (name != null) {
            this.name = name;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
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
