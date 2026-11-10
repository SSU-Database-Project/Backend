package com.ssu.shop.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.domain.user.dto.AuthDtos.SellerInfoRequest;
import com.ssu.shop.domain.user.dto.AuthDtos.SignupRequest;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    SellerRepository sellerRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtProvider jwtProvider;
    @InjectMocks
    AuthService authService;

    private SignupRequest signup(Role role, SellerInfoRequest seller) {
        return new SignupRequest("New@Test.com", "password1!", "홍길동", "010-1234-5678", role, seller);
    }

    @Test
    void 일반회원으로_가입하면_판매자_정보는_만들지_않는다() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hash");

        var response = authService.signup(signup(Role.USER, null));

        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.email()).isEqualTo("new@test.com"); // 소문자로 저장
        verify(sellerRepository, never()).saveAndFlush(any());
    }

    @Test
    void 판매자로_가입하면_같은_트랜잭션에서_판매자_정보를_만든다() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hash");

        authService.signup(signup(Role.SELLER, new SellerInfoRequest("123-45-67890", "어반스타일", "김판매")));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(user.capture());
        assertThat(user.getValue().getRole()).isEqualTo(Role.SELLER);

        ArgumentCaptor<Seller> seller = ArgumentCaptor.forClass(Seller.class);
        verify(sellerRepository).saveAndFlush(seller.capture());
        assertThat(seller.getValue().getBusinessNumber()).isEqualTo("1234567890"); // 하이픈 제거
        assertThat(seller.getValue().getUser()).isSameAs(user.getValue());
    }

    @Test
    void 관리자로는_가입할_수_없다() {
        assertThatThrownBy(() -> authService.signup(signup(Role.ADMIN, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_등록된_사업자번호면_409() {
        when(sellerRepository.existsByBusinessNumber("1234567890")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(
                signup(Role.SELLER, new SellerInfoRequest("1234567890", "회사", "대표"))))
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
        verify(userRepository, never()).saveAndFlush(any());
    }
}
