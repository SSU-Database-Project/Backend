package com.ssu.shop.domain.like.dto;

public final class LikeDtos {

    private LikeDtos() {
    }

    /** 하트 버튼 상태: 내가 눌렀는지 + 전체 좋아요 수 */
    public record LikeStatusResponse(Long productId, boolean liked, long likeCount) {
    }
}
