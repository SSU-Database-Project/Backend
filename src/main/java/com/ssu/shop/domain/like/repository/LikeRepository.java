package com.ssu.shop.domain.like.repository;

import com.ssu.shop.domain.like.entity.Like;
import com.ssu.shop.domain.like.entity.Like.LikeId;
import com.ssu.shop.domain.product.entity.ProductStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, LikeId> {

    /** 상품별 좋아요 수 — idx_likes_product 사용 */
    @Query("select count(l) from ProductLike l where l.id.productId = :productId")
    long countByProductId(@Param("productId") Long productId);

    /**
     * 내 좋아요 목록 (최근에 누른 순). 상품·브랜드·카테고리를 fetch join 해서 상품마다 추가 쿼리가 나가지 않는다.
     * idx_likes_user_created (user_id, created_at) 로 user_id 를 찾고 created_at 순서로 읽는다.
     * 구매자에게 보이는 상품(ON_SALE, SOLD_OUT)만 — 숨겨지거나 삭제된 상품은 목록에서 빠진다.
     */
    @Query(value = """
            select l from ProductLike l
              join fetch l.product p join fetch p.brand join fetch p.category
             where l.id.userId = :userId and p.status in :statuses
            """,
            countQuery = """
            select count(l) from ProductLike l join l.product p
             where l.id.userId = :userId and p.status in :statuses
            """)
    Page<Like> findMyLikes(@Param("userId") Long userId, @Param("statuses") Collection<ProductStatus> statuses,
                           Pageable pageable);
}
