package com.ssu.shop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.order.entity.Shipment;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.order.repository.OrderItemRepository;
import com.ssu.shop.domain.order.repository.ShipmentRepository;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** 판매자 배송 상태 변경 규칙. 실제 상태 전이와 동시 요청은 OrderManageDbTest 에서 MySQL 로 확인한다. */
@ExtendWith(MockitoExtension.class)
class SellerShipmentServiceTest {

    static final long MY_USER_ID = 100L;
    static final long SHIPMENT_ID = 7L;

    @Mock
    ShipmentRepository shipmentRepository;
    @Mock
    OrderItemRepository orderItemRepository;
    @Mock
    SellerService sellerService;
    @InjectMocks
    SellerShipmentService sellerShipmentService;

    Seller me;
    Seller other;

    @BeforeEach
    void setUp() {
        me = Fixtures.seller(1L, Fixtures.user(MY_USER_ID, "me@test.com"));
        other = Fixtures.seller(2L, Fixtures.user(200L, "other@test.com"));
        when(sellerService.getSeller(MY_USER_ID)).thenReturn(me);
    }

    @Test
    void 바꾸려는_상태의_바로_앞_상태일_때만_바뀌도록_요청한다() {
        givenShipmentOf(me);
        when(shipmentRepository.transit(anyLong(), any(), any())).thenReturn(0); // 결과는 여기서 보지 않는다

        for (ShipmentStatus[] step : new ShipmentStatus[][] {
                {ShipmentStatus.ORDERED, ShipmentStatus.PREPARING},
                {ShipmentStatus.PREPARING, ShipmentStatus.SHIPPING},
                {ShipmentStatus.SHIPPING, ShipmentStatus.DELIVERED}}) {
            assertError(ErrorCode.INVALID_ORDER_STATUS,
                    () -> sellerShipmentService.changeStatus(MY_USER_ID, SHIPMENT_ID, step[1]));
            verify(shipmentRepository).transit(SHIPMENT_ID, step[0], step[1]);
        }
    }

    @Test
    void 판매자는_주문완료_취소_구매확정으로는_바꿀_수_없다() {
        givenShipmentOf(me);

        for (ShipmentStatus target : new ShipmentStatus[] {ShipmentStatus.ORDERED, ShipmentStatus.CANCELED,
                ShipmentStatus.CONFIRMED}) {
            assertError(ErrorCode.INVALID_INPUT,
                    () -> sellerShipmentService.changeStatus(MY_USER_ID, SHIPMENT_ID, target));
        }
        verify(shipmentRepository, never()).transit(anyLong(), any(), any());
    }

    @Test
    void 남의_배송_묶음은_없는_것처럼_NOT_FOUND() {
        givenShipmentOf(other);

        assertError(ErrorCode.NOT_FOUND,
                () -> sellerShipmentService.changeStatus(MY_USER_ID, SHIPMENT_ID, ShipmentStatus.PREPARING));
        verify(shipmentRepository, never()).transit(anyLong(), any(), any());
    }

    private void givenShipmentOf(Seller seller) {
        Shipment shipment = BeanUtils.instantiateClass(Shipment.class);
        ReflectionTestUtils.setField(shipment, "id", SHIPMENT_ID);
        ReflectionTestUtils.setField(shipment, "seller", seller);
        when(shipmentRepository.findById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
    }

    private void assertError(ErrorCode expected, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
