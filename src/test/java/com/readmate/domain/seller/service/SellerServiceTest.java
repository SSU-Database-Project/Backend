package com.readmate.domain.seller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.readmate.domain.seller.dto.SellerDtos.SellerApplyRequest;
import com.readmate.domain.seller.dto.SellerDtos.SellerStatusChangeRequest;
import com.readmate.domain.seller.entity.Seller;
import com.readmate.domain.seller.entity.SellerStatus;
import com.readmate.domain.seller.repository.SellerRepository;
import com.readmate.domain.user.entity.Role;
import com.readmate.domain.user.entity.User;
import com.readmate.domain.user.repository.UserRepository;
import com.readmate.global.error.BusinessException;
import com.readmate.global.error.ErrorCode;
import com.readmate.support.Fixtures;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock
    SellerRepository sellerRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    SellerService sellerService;

    @Test
    void 승인하면_같은_트랜잭션에서_회원_role이_SELLER로_바뀐다() {
        User user = Fixtures.user(1L, "a@test.com");
        Seller seller = Fixtures.seller(10L, user, SellerStatus.APPROVED);
        when(sellerRepository.transitStatus(eq(10L), eq(SellerStatus.PENDING), eq(SellerStatus.APPROVED),
                isNull(), any())).thenReturn(1);
        when(sellerRepository.findById(10L)).thenReturn(Optional.of(seller));

        sellerService.changeStatus(10L, new SellerStatusChangeRequest(SellerStatus.APPROVED, null));

        assertThat(user.getRole()).isEqualTo(Role.SELLER);
    }

    @Test
    void 거절하면_role은_USER로_유지된다() {
        User user = Fixtures.user(1L, "a@test.com");
        Seller seller = Fixtures.seller(10L, user, SellerStatus.REJECTED);
        when(sellerRepository.transitStatus(eq(10L), eq(SellerStatus.PENDING), eq(SellerStatus.REJECTED),
                eq("서류 미비"), isNull())).thenReturn(1);
        when(sellerRepository.findById(10L)).thenReturn(Optional.of(seller));

        sellerService.changeStatus(10L, new SellerStatusChangeRequest(SellerStatus.REJECTED, "서류 미비"));

        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void 이미_처리된_신청은_조건부_UPDATE_영향행이_0이라_409() {
        when(sellerRepository.transitStatus(eq(10L), any(), any(), any(), any())).thenReturn(0);
        when(sellerRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> sellerService.changeStatus(10L,
                new SellerStatusChangeRequest(SellerStatus.APPROVED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void PENDING_상태에서_재신청하면_409() {
        User user = Fixtures.user(1L, "a@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sellerRepository.findByBusinessNumber(anyString())).thenReturn(Optional.empty());
        when(sellerRepository.findByUserId(1L))
                .thenReturn(Optional.of(Fixtures.seller(10L, user, SellerStatus.PENDING)));

        assertThatThrownBy(() -> sellerService.apply(1L, new SellerApplyRequest("123-45-67890", "회사", "대표")))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_SELLER_APPLIED);
        verify(sellerRepository, never()).saveAndFlush(any());
    }

    @Test
    void 거절된_회원은_기존_행을_갱신해_재신청한다() {
        User user = Fixtures.user(1L, "a@test.com");
        Seller rejected = Fixtures.seller(10L, user, SellerStatus.REJECTED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sellerRepository.findByBusinessNumber("1234567890")).thenReturn(Optional.empty());
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(rejected));

        sellerService.apply(1L, new SellerApplyRequest("123-45-67890", "새회사", "대표"));

        assertThat(rejected.getStatus()).isEqualTo(SellerStatus.PENDING);
        assertThat(rejected.getBusinessNumber()).isEqualTo("1234567890"); // 하이픈 제거 후 저장
        assertThat(rejected.getCompanyName()).isEqualTo("새회사");
        verify(sellerRepository).saveAndFlush(rejected); // 새 행이 아닌 같은 엔티티
    }

    @Test
    void 다른_회원이_쓰는_사업자번호면_409() {
        User me = Fixtures.user(1L, "a@test.com");
        User other = Fixtures.user(2L, "b@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(sellerRepository.findByBusinessNumber("1234567890"))
                .thenReturn(Optional.of(Fixtures.seller(20L, other, SellerStatus.APPROVED)));

        assertThatThrownBy(() -> sellerService.apply(1L, new SellerApplyRequest("1234567890", "회사", "대표")))
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
    }
}
