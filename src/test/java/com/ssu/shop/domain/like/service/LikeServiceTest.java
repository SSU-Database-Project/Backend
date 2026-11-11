package com.ssu.shop.domain.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.like.entity.Like;
import com.ssu.shop.domain.like.entity.Like.LikeId;
import com.ssu.shop.domain.like.repository.LikeRepository;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.entity.SizeGroup;
import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    LikeRepository likeRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ProductService productService;
    @InjectMocks
    LikeService likeService;

    private Product product(ProductStatus status) {
        var seller = Fixtures.seller(1L, Fixtures.user(1L, "s@test.com"));
        var leaf = Fixtures.leafCategory(11L, Fixtures.rootCategory(1L, SizeGroup.CLOTHING));
        return Fixtures.product(50L, Fixtures.brand(10L, seller, BrandStatus.APPROVED), leaf, status);
    }

    @Test
    void 좋아요를_누르면_저장하고_상태를_돌려준다() {
        when(productRepository.findById(50L)).thenReturn(Optional.of(product(ProductStatus.ON_SALE)));
        when(likeRepository.existsById(new LikeId(7L, 50L))).thenReturn(false, true);
        when(userRepository.getReferenceById(7L)).thenReturn(Fixtures.user(7L, "buyer@test.com"));
        when(likeRepository.countByProductId(50L)).thenReturn(3L);

        var status = likeService.like(7L, 50L);

        verify(likeRepository).saveAndFlush(any(Like.class));
        assertThat(status.liked()).isTrue();
        assertThat(status.likeCount()).isEqualTo(3L);
    }

    @Test
    void 이미_누른_상품은_다시_저장하지_않는다() {
        when(productRepository.findById(50L)).thenReturn(Optional.of(product(ProductStatus.ON_SALE)));
        when(likeRepository.existsById(new LikeId(7L, 50L))).thenReturn(true);

        likeService.like(7L, 50L);

        verify(likeRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시에_두_번_눌러_PK가_막아도_성공으로_처리한다() {
        when(productRepository.findById(50L)).thenReturn(Optional.of(product(ProductStatus.ON_SALE)));
        when(likeRepository.existsById(new LikeId(7L, 50L))).thenReturn(false, true);
        when(userRepository.getReferenceById(7L)).thenReturn(Fixtures.user(7L, "buyer@test.com"));
        when(likeRepository.saveAndFlush(any(Like.class))).thenThrow(new DataIntegrityViolationException("pk_likes"));

        assertThat(likeService.like(7L, 50L).liked()).isTrue();
    }

    @Test
    void 구매자에게_보이지_않는_상품은_좋아요할_수_없다() {
        when(productRepository.findById(50L)).thenReturn(Optional.of(product(ProductStatus.PENDING)));

        assertThatThrownBy(() -> likeService.like(7L, 50L))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        verify(likeRepository, never()).saveAndFlush(any());
    }
}
