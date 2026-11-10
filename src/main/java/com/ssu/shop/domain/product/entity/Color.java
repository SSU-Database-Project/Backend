package com.ssu.shop.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** colors: 미리 정한 색상 목록 (참조 테이블, V4 시드). 판매자는 여기서 고른다. */
@Entity
@Table(name = "colors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Color {

    @Id
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "hex_code", nullable = false, length = 7, columnDefinition = "char(7)")
    private String hexCode;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
