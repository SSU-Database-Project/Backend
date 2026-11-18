package com.ssu.shop.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.order.repository.ShipmentRepository;
import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.entity.UserStatus;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    SellerRepository sellerRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    ShipmentRepository shipmentRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        AccountLifecycle lifecycle = new AccountLifecycle(userRepository, sellerRepository, productRepository,
                shipmentRepository, passwordEncoder);
        adminUserService = new AdminUserService(userRepository, lifecycle);
    }

    @Test
    void 탈퇴하면_개인정보를_지우고_행은_남긴다() {
        User buyer = Fixtures.user(5L, "buyer@test.com");
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(buyer));
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}random");

        adminUserService.withdraw(5L);

        assertThat(buyer.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(buyer.getWithdrawnAt()).isNotNull();
        assertThat(buyer.getEmail()).isEqualTo("withdrawn_5@withdrawn.local");
        assertThat(buyer.getName()).isEqualTo("탈퇴회원");
        assertThat(buyer.getPhone()).isNull();
        verify(shipmentRepository).countInProgressByBuyerForUpdate(5L);
        verify(productRepository, never()).findActiveIdsBySeller(anyLong());
        verify(productRepository, never()).markDeleted(anyLong());
    }

    @Test
    void 판매자가_탈퇴하면_상품을_id_오름차순으로_한_행씩_판매_종료한_뒤_진행_중_주문을_센다() {
        User sellerUser = Fixtures.user(6L, "seller@test.com", Role.SELLER);
        when(userRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(sellerUser));
        when(sellerRepository.findByUserId(6L)).thenReturn(Optional.of(Fixtures.seller(60L, sellerUser)));
        when(productRepository.findActiveIdsBySeller(60L)).thenReturn(List.of(3L, 8L, 21L));
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}random");

        adminUserService.withdraw(6L);

        // 주문(OrderService.create)과 같은 순서로 잠가야 데드락이 없다. 상품을 다 잠근 뒤에 세야 동시 주문을 놓치지 않는다
        InOrder inOrder = inOrder(productRepository, shipmentRepository);
        inOrder.verify(productRepository).markDeleted(3L);
        inOrder.verify(productRepository).markDeleted(8L);
        inOrder.verify(productRepository).markDeleted(21L);
        inOrder.verify(shipmentRepository).countInProgressBySellerForUpdate(60L);
        assertThat(sellerUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    void 진행_중인_주문이_있는_구매자는_탈퇴시킬_수_없다() {
        User buyer = Fixtures.user(5L, "buyer@test.com");
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(buyer));
        when(shipmentRepository.countInProgressByBuyerForUpdate(5L)).thenReturn(2L);

        assertThatThrownBy(() -> adminUserService.withdraw(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2건")
                .extracting("errorCode").isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
        assertThat(buyer.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 진행_중인_주문이_있는_판매자는_정지시킬_수_없다() {
        User sellerUser = Fixtures.user(6L, "seller@test.com", Role.SELLER);
        when(userRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(sellerUser));
        when(sellerRepository.findByUserId(6L)).thenReturn(Optional.of(Fixtures.seller(60L, sellerUser)));
        when(shipmentRepository.countInProgressBySellerForUpdate(60L)).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.changeStatus(6L, UserStatus.SUSPENDED))
                .extracting("errorCode").isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
        assertThat(sellerUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 진행_중인_주문이_없으면_정지되고_해제는_주문과_상관없이_된다() {
        User buyer = Fixtures.user(5L, "buyer@test.com");
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(buyer));

        adminUserService.changeStatus(5L, UserStatus.SUSPENDED);
        assertThat(buyer.getStatus()).isEqualTo(UserStatus.SUSPENDED);

        adminUserService.changeStatus(5L, UserStatus.ACTIVE);
        assertThat(buyer.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(shipmentRepository, org.mockito.Mockito.times(1)).countInProgressByBuyerForUpdate(5L);
    }

    @Test
    void 관리자_계정은_정지나_탈퇴할_수_없다() {
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(Fixtures.user(1L, "admin@test.com", Role.ADMIN)));

        assertThatThrownBy(() -> adminUserService.withdraw(1L))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_MODIFY_ADMIN);
        assertThatThrownBy(() -> adminUserService.changeStatus(1L, UserStatus.SUSPENDED))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_MODIFY_ADMIN);
    }

    @Test
    void 정지_API로는_탈퇴_상태를_지정할_수_없다() {
        assertThatThrownBy(() -> adminUserService.changeStatus(5L, UserStatus.WITHDRAWN))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
