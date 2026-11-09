package com.readmate.domain.user.service;

import com.readmate.domain.seller.repository.SellerRepository;
import com.readmate.domain.user.dto.AuthDtos.LoginRequest;
import com.readmate.domain.user.dto.AuthDtos.MeResponse;
import com.readmate.domain.user.dto.AuthDtos.SignupRequest;
import com.readmate.domain.user.dto.AuthDtos.SignupResponse;
import com.readmate.domain.user.dto.AuthDtos.TokenResponse;
import com.readmate.domain.user.entity.User;
import com.readmate.domain.user.repository.UserRepository;
import com.readmate.global.error.BusinessException;
import com.readmate.global.error.ErrorCode;
import com.readmate.global.security.JwtProvider;
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
     * 회원가입.
     * existsByEmail 은 친절한 에러 메시지를 위한 1차 확인일 뿐이다. 확인과 INSERT 사이에 같은 이메일로
     * 동시 가입이 끼어들 수 있으므로(check-then-act 경쟁), 최종 보장은 DB 의 uk_users_email 이 한다.
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        User user = User.create(email, passwordEncoder.encode(request.password()), request.name().trim(),
                normalizePhone(request.phone()));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        return new SignupResponse(user.getId(), user.getEmail());
    }

    /** 이메일 존재 여부와 비밀번호 불일치를 구분하지 않는다(계정 존재 여부 노출 방지). */
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
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
        return MeResponse.of(user, sellerRepository.findByUserId(user.getId()).orElse(null));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizePhone(String phone) {
        return (phone == null || phone.isBlank()) ? null : phone.replace("-", "");
    }
}
