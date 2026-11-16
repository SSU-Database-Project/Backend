package com.ssu.shop.domain.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.inventory.dto.StockDtos.StockChangeRequest;
import com.ssu.shop.domain.inventory.entity.StockType;
import com.ssu.shop.domain.inventory.repository.StockHistoryRepository;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.entity.SizeGroup;
import com.ssu.shop.domain.product.repository.ProductOptionRepository;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 판매자 재고 API 의 규칙: 본인 상품만, 삭제된 상품은 거부, type 에 따라 입고·조정으로 넘긴다 */
@ExtendWith(MockitoExtension.class)
class SellerStockServiceTest {

    static final long MY_USER_ID = 100L;
    static final long OPTION_ID = 7L;

    @Mock
    StockService stockService;
    @Mock
    ProductOptionRepository optionRepository;
    @Mock
    StockHistoryRepository stockHistoryRepository;
    @Mock
    SellerService sellerService;
    @InjectMocks
    SellerStockService sellerStockService;

    Seller me;
    Seller other;

    @BeforeEach
    void setUp() {
        me = Fixtures.seller(1L, Fixtures.user(MY_USER_ID, "me@test.com"));
        other = Fixtures.seller(2L, Fixtures.user(200L, "other@test.com"));
        when(sellerService.getSeller(MY_USER_ID)).thenReturn(me);
    }

    @Test
    void IN_은_입고로_넘긴다() {
        givenOption(me, ProductStatus.ON_SALE);

        sellerStockService.change(MY_USER_ID, OPTION_ID, new StockChangeRequest(StockType.IN, 20, "11월 입고"));

        verify(stockService).inbound(OPTION_ID, 20, "11월 입고");
    }

    @Test
    void ADJUST_는_조정으로_넘긴다_승인_대기_상품도_가능하다() {
        givenOption(me, ProductStatus.PENDING); // 재고 변경은 승인이 필요 없다

        sellerStockService.change(MY_USER_ID, OPTION_ID, new StockChangeRequest(StockType.ADJUST, -3, "파손"));

        verify(stockService).adjust(OPTION_ID, -3, "파손");
    }

    @Test
    void 남의_상품_옵션이면_PRODUCT_NOT_OWNED() {
        givenOption(other, ProductStatus.ON_SALE);

        assertThatThrownBy(() -> sellerStockService.change(MY_USER_ID, OPTION_ID,
                new StockChangeRequest(StockType.IN, 20, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_OWNED));
        verifyNoInteractions(stockService);
    }

    @Test
    void 남의_상품_옵션의_재고_이력도_볼_수_없다() {
        givenOption(other, ProductStatus.ON_SALE);

        assertThatThrownBy(() -> sellerStockService.history(MY_USER_ID, OPTION_ID, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_OWNED));
        verifyNoInteractions(stockHistoryRepository);
    }

    @Test
    void 삭제된_상품이면_PRODUCT_NOT_EDITABLE() {
        givenOption(me, ProductStatus.DELETED);

        assertThatThrownBy(() -> sellerStockService.change(MY_USER_ID, OPTION_ID,
                new StockChangeRequest(StockType.IN, 20, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_EDITABLE));
        verifyNoInteractions(stockService);
    }

    @Test
    void 없는_옵션이면_NOT_FOUND() {
        when(optionRepository.findById(OPTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerStockService.change(MY_USER_ID, OPTION_ID,
                new StockChangeRequest(StockType.IN, 20, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void 주문_취소_유형은_직접_요청할_수_없다() {
        givenOption(me, ProductStatus.ON_SALE);

        assertThatThrownBy(() -> sellerStockService.change(MY_USER_ID, OPTION_ID,
                new StockChangeRequest(StockType.ORDER, 1, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verifyNoInteractions(stockService);
    }

    /** owner 의 브랜드에 속한 상품의 옵션을 준비한다 */
    private void givenOption(Seller owner, ProductStatus status) {
        Brand brand = Fixtures.brand(10L, owner, BrandStatus.APPROVED);
        Product product = Fixtures.product(5L, brand,
                Fixtures.leafCategory(11L, Fixtures.rootCategory(1L, SizeGroup.CLOTHING)), status);
        ProductOption option = ProductOption.create(product, null, null, 10);
        ReflectionTestUtils.setField(option, "id", OPTION_ID);
        when(optionRepository.findById(OPTION_ID)).thenReturn(Optional.of(option));
    }
}
