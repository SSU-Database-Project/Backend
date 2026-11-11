package com.ssu.shop.domain.like.service;

import com.ssu.shop.domain.like.dto.LikeDtos.LikeStatusResponse;
import com.ssu.shop.domain.like.entity.Like;
import com.ssu.shop.domain.like.entity.Like.LikeId;
import com.ssu.shop.domain.like.repository.LikeRepository;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    /**
     * 좋아요. 이미 눌렀으면 아무 일도 하지 않는다(멱등) — 버튼을 두 번 눌러도 결과가 같다.
     * 동시에 두 번 들어오면 사전 확인을 둘 다 통과할 수 있는데, 복합 PK(user_id, product_id)가 두 번째 INSERT 를 거부한다.
     */
    @Transactional
    public LikeStatusResponse like(Long userId, Long productId) {
        Product product = getVisibleProduct(productId);
        LikeId id = new LikeId(userId, productId);
        if (!likeRepository.existsById(id)) {
            try {
                likeRepository.saveAndFlush(Like.create(userRepository.getReferenceById(userId), product));
            } catch (DataIntegrityViolationException e) {
                // 동시 요청이 먼저 넣었다 → 이미 좋아요 상태이므로 그대로 성공 처리
            }
        }
        return status(userId, productId);
    }

    /** 좋아요 취소. 누르지 않았던 상품이어도 성공(멱등). 숨겨진 상품의 좋아요도 취소할 수 있게 상태는 보지 않는다. */
    @Transactional
    public LikeStatusResponse unlike(Long userId, Long productId) {
        if (!productRepository.existsById(productId)) {
            throw BusinessException.notFound("상품");
        }
        likeRepository.deleteById(new LikeId(userId, productId));
        likeRepository.flush();
        return status(userId, productId);
    }

    public LikeStatusResponse status(Long userId, Long productId) {
        return new LikeStatusResponse(productId, likeRepository.existsById(new LikeId(userId, productId)),
                likeRepository.countByProductId(productId));
    }

    /** 마이페이지 좋아요 목록: 최근에 누른 순, 구매자에게 보이는 상품만 */
    public PageResponse<ProductSummaryResponse> myLikes(Long userId, int page, int size) {
        Page<Like> likes = likeRepository.findMyLikes(userId, ProductStatus.PUBLIC_STATUSES,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id.productId"))));
        List<Product> products = likes.getContent().stream().map(Like::getProduct).toList();
        return PageResponse.of(likes, productService.summarize(products));
    }

    private Product getVisibleProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(p -> p.getStatus().isPublic())
                .orElseThrow(() -> BusinessException.notFound("상품"));
    }
}
