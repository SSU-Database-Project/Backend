package com.ssu.shop.domain.like.entity;

import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * likes: 회원 ↔ 상품 N:M 의 연관 엔티티. PK = (user_id, product_id) 복합키 → 같은 상품에 두 번 좋아요 불가.
 * 별도 id 없이 두 FK 를 PK 로 쓰는 식별 관계다.
 */
@Entity(name = "ProductLike") // JPQL 에서 "Like" 는 LIKE 연산자와 겹치므로 엔티티 이름을 바꾼다
@Table(name = "likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Like {

    @EmbeddedId
    private LikeId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Like(User user, Product product) {
        this.id = new LikeId(user.getId(), product.getId());
        this.user = user;
        this.product = product;
    }

    public static Like create(User user, Product product) {
        return new Like(user, product);
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class LikeId implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "product_id")
        private Long productId;

        public LikeId(Long userId, Long productId) {
            this.userId = userId;
            this.productId = productId;
        }
    }
}
