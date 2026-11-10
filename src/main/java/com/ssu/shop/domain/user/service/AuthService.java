package com.ssu.shop.domain.user.service;

import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.repository.SellerRepository;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.domain.user.dto.AuthDtos.LoginRequest;
import com.ssu.shop.domain.user.dto.AuthDtos.MeResponse;
import com.ssu.shop.domain.user.dto.AuthDtos.SellerInfoRequest;
import com.ssu.shop.domain.user.dto.AuthDtos.SignupRequest;
import com.ssu.shop.domain.user.dto.AuthDtos.SignupResponse;
import com.ssu.shop.domain.user.dto.AuthDtos.TokenResponse;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.DbConstraints;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.global.security.JwtProvider;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * 회원가입 (일반회원 / 판매자).
     * 판매자는 users 와 sellers 를 한 트랜잭션에서 만든다 → "role = SELLER 인데 판매자 정보가 없는" 상태가 생기지 않는다.
     * 사전 중복 확인은 친절한 에러 메시지용이고, 동시 가입은 DB UNIQUE(uk_users_email, uk_sellers_business_number)가 최종적으로 막는다.
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "관리자 계정은 가입으로 만들 수 없습니다.");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        SellerInfoRequest sellerInfo = request.seller();
        String businessNumber = null;
        if (request.role() == Role.SELLER) {
            businessNumber = SellerService.normalizeBusinessNumber(sellerInfo.businessNumber());
            if (sellerRepository.existsByBusinessNumber(businessNumber)) {
                throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
            }
        }

        User user = User.create(email, passwordEncoder.encode(request.password()), request.name().trim(),
                normalizePhone(request.phone()), request.role());
        try {
            userRepository.saveAndFlush(user);
            if (request.role() == Role.SELLER) {
                sellerRepository.saveAndFlush(Seller.create(user, businessNumber, sellerInfo.companyName().trim(),
                        sellerInfo.representativeName().trim()));
            }
        } catch (DataIntegrityViolationException e) {
            if (DbConstraints.isViolated(e, "uk_sellers_business_number")) {
                throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
            }
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        return new SignupResponse(user.getId(), user.getEmail(), user.getRole());
    }

    /**
     * 이메일 존재 여부와 비밀번호 불일치를 구분하지 않는다(계정 존재 여부 노출 방지).
     * 탈퇴 회원은 이메일이 바뀌어 있어 여기서 찾히지 않는다.
     */
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(u -> !u.isWithdrawn())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        String token = jwtProvider.createAccessToken(user.getId(), user.getRole());
        return TokenResponse.bearer(token, jwtProvider.getValiditySeconds(), toMe(user));
    }

    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("회원"));
        return toMe(user);
    }

    private MeResponse toMe(User user) {
        Seller seller = user.getRole() == Role.SELLER ? sellerRepository.findByUserId(user.getId()).orElse(null) : null;
        return MeResponse.of(user, seller);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizePhone(String phone) {
        return (phone == null || phone.isBlank()) ? null : phone.replace("-", "");
    }
}
