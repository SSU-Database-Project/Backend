package com.ssu.shop.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.entity.UserStatus;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    PasswordEncoder passwordEncoder;
    @InjectMocks
    AdminUserService adminUserService;

    @Test
    void 탈퇴하면_개인정보를_지우고_행은_남긴다() {
        User buyer = Fixtures.user(5L, "buyer@test.com");
        when(userRepository.findById(5L)).thenReturn(Optional.of(buyer));
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}random");

        adminUserService.withdraw(5L);

        assertThat(buyer.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(buyer.getWithdrawnAt()).isNotNull();
        assertThat(buyer.getEmail()).isEqualTo("withdrawn_5@withdrawn.local");
        assertThat(buyer.getName()).isEqualTo("탈퇴회원");
        assertThat(buyer.getPhone()).isNull();
        verify(productRepository, never()).deleteAllBySeller(anyLong());
    }

    @Test
    void 판매자가_탈퇴하면_그_판매자의_상품을_판매_종료한다() {
        User sellerUser = Fixtures.user(6L, "seller@test.com", Role.SELLER);
        when(userRepository.findById(6L)).thenReturn(Optional.of(sellerUser));
        when(sellerRepository.findByUserId(6L)).thenReturn(Optional.of(Fixtures.seller(60L, sellerUser)));
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}random");

        adminUserService.withdraw(6L);

        verify(productRepository).deleteAllBySeller(60L);
    }

    @Test
    void 관리자_계정은_정지나_탈퇴할_수_없다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(Fixtures.user(1L, "admin@test.com", Role.ADMIN)));

        assertThatThrownBy(() -> adminUserService.withdraw(1L))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_MODIFY_ADMIN);
        assertThatThrownBy(() -> adminUserService.changeStatus(1L, UserStatus.SUSPENDED))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_MODIFY_ADMIN);
    }

    @Test
    void 정지_API로는_탈퇴_상태를_지정할_수_없다() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(Fixtures.user(5L, "buyer@test.com")));

        assertThatThrownBy(() -> adminUserService.changeStatus(5L, UserStatus.WITHDRAWN))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
