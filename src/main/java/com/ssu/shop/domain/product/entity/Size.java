package com.ssu.shop.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** sizes: 미리 정한 사이즈 목록 (참조 테이블, V4 시드). size_group 별로 나뉜다. */
@Entity
@Table(name = "sizes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Size {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "size_group", nullable = false, length = 20)
    private SizeGroup sizeGroup;

    @Column(nullable = false, length = 10)
    private String label;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
