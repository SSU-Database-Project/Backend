package com.readmate.global.config;

import com.readmate.domain.user.entity.User;
import com.readmate.domain.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정 1개를 앱 시작 시 생성(없을 때만).
 * SQL 시드에 BCrypt 해시를 하드코딩하지 않고 PasswordEncoder 로 만들기 위해 Flyway 가 아닌 코드로 처리한다.
 * 계정 정보는 ADMIN_EMAIL / ADMIN_PASSWORD 환경변수로 바꿀 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = appProperties.admin();
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.createAdmin(email, passwordEncoder.encode(admin.password()), admin.name()));
        log.info("관리자 계정 생성: {}", email);
    }
}
