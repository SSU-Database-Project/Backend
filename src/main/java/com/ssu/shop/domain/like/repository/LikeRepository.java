package com.ssu.shop.domain.like.repository;

import com.ssu.shop.domain.like.entity.Like;
import com.ssu.shop.domain.like.entity.Like.LikeId;
import org.springframework.data.jpa.repository.JpaRepository;

/** 좋아요 추가/취소/목록 API 는 담당 확정 후 추가한다. */
public interface LikeRepository extends JpaRepository<Like, LikeId> {
}
