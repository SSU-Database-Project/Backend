package com.ssu.shop.domain.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.inventory.entity.StockHistory;
import com.ssu.shop.domain.inventory.entity.StockType;
import com.ssu.shop.domain.inventory.repository.StockHistoryRepository;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.repository.ProductOptionRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** 재고 변경 규칙(검증·증감·이력 내용)을 DB 없이 확인한다. 잠금과 동시성은 StockServiceDbTest 에서 실제 MySQL 로 확인한다. */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    ProductOptionRepository optionRepository;
    @Mock
    StockHistoryRepository stockHistoryRepository;
    @Mock
    EntityManager entityManager;
    @InjectMocks
    StockService stockService;

    @Test
    void 주문_차감은_재고를_줄이고_주문줄이_연결된_ORDER_이력을_남긴다() {
        ProductOption option = lockedOption(1L, 10);
        OrderItem item = orderItem(100L, option, 3);

        stockService.decrease(List.of(item));

        assertThat(option.getStockQuantity()).isEqualTo(7);
        StockHistory history = savedHistory();
        assertThat(history.getType()).isEqualTo(StockType.ORDER);
        assertThat(history.getChangeQty()).isEqualTo(-3);
        assertThat(history.getQuantityAfter()).isEqualTo(7);
        assertThat(history.getOrderItem()).isSameAs(item);
        // 잠근 뒤 DB 최신 값으로 다시 읽는다
        verify(entityManager).refresh(option, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void 재고보다_많이_주문하면_OUT_OF_STOCK_이고_재고와_이력은_그대로다() {
        ProductOption option = lockedOption(1L, 2);

        assertThatThrownBy(() -> stockService.decrease(List.of(orderItem(100L, option, 3))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK));

        assertThat(option.getStockQuantity()).isEqualTo(2);
        verify(stockHistoryRepository, never()).save(any());
    }

    @Test
    void 없는_옵션이면_NOT_FOUND() {
        ProductOption missing = option(99L, 5);
        when(optionRepository.lockAllByIdInOrder(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> stockService.decrease(List.of(orderItem(100L, missing, 1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(stockHistoryRepository, never()).save(any());
    }

    @Test
    void 취소_복원은_재고를_되돌리고_주문줄이_연결된_CANCEL_이력을_남긴다() {
        ProductOption option = lockedOption(1L, 7);
        OrderItem item = orderItem(100L, option, 3);

        stockService.restore(List.of(item));

        assertThat(option.getStockQuantity()).isEqualTo(10);
        StockHistory history = savedHistory();
        assertThat(history.getType()).isEqualTo(StockType.CANCEL);
        assertThat(history.getChangeQty()).isEqualTo(3);
        assertThat(history.getQuantityAfter()).isEqualTo(10);
        assertThat(history.getOrderItem()).isSameAs(item);
    }

    @Test
    void 입고는_재고를_늘리고_메모와_함께_IN_이력을_남긴다() {
        ProductOption option = lockedOption(1L, 10);

        stockService.inbound(1L, 5, "11월 입고");

        assertThat(option.getStockQuantity()).isEqualTo(15);
        StockHistory history = savedHistory();
        assertThat(history.getType()).isEqualTo(StockType.IN);
        assertThat(history.getChangeQty()).isEqualTo(5);
        assertThat(history.getQuantityAfter()).isEqualTo(15);
        assertThat(history.getMemo()).isEqualTo("11월 입고");
        assertThat(history.getOrderItem()).isNull();
    }

    @Test
    void 입고_수량이_0_이하면_INVALID_INPUT() {
        assertThatThrownBy(() -> stockService.inbound(1L, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verifyNoInteractions(optionRepository, stockHistoryRepository);
    }

    @Test
    void 조정은_재고를_줄일_수도_있고_ADJUST_이력을_남긴다() {
        ProductOption option = lockedOption(1L, 10);

        stockService.adjust(1L, -4, "파손");

        assertThat(option.getStockQuantity()).isEqualTo(6);
        StockHistory history = savedHistory();
        assertThat(history.getType()).isEqualTo(StockType.ADJUST);
        assertThat(history.getChangeQty()).isEqualTo(-4);
        assertThat(history.getQuantityAfter()).isEqualTo(6);
        assertThat(history.getMemo()).isEqualTo("파손");
    }

    @Test
    void 조정_결과가_음수면_INVALID_INPUT_이고_재고와_이력은_그대로다() {
        ProductOption option = lockedOption(1L, 3);

        assertThatThrownBy(() -> stockService.adjust(1L, -4, "파손"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        assertThat(option.getStockQuantity()).isEqualTo(3);
        verify(stockHistoryRepository, never()).save(any());
    }

    @Test
    void 조정_수량이_0이면_INVALID_INPUT() {
        assertThatThrownBy(() -> stockService.adjust(1L, 0, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verifyNoInteractions(optionRepository, stockHistoryRepository);
    }

    // ===== helpers =====

    private ProductOption option(long id, int stock) {
        ProductOption option = ProductOption.create(null, null, null, stock);
        ReflectionTestUtils.setField(option, "id", id);
        return option;
    }

    /** 잠금 조회가 이 옵션을 돌려주도록 준비한다 */
    private ProductOption lockedOption(long id, int stock) {
        ProductOption option = option(id, stock);
        when(optionRepository.lockAllByIdInOrder(anyCollection())).thenReturn(List.of(id));
        when(optionRepository.findAllById(any())).thenReturn(List.of(option));
        return option;
    }

    private OrderItem orderItem(long id, ProductOption option, int quantity) {
        OrderItem item = BeanUtils.instantiateClass(OrderItem.class);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "option", option);
        ReflectionTestUtils.setField(item, "quantity", quantity);
        return item;
    }

    private StockHistory savedHistory() {
        ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(captor.capture());
        return captor.getValue();
    }
}
