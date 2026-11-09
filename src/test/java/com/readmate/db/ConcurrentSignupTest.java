package com.readmate.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.readmate.domain.user.dto.AuthDtos.SignupRequest;
import com.readmate.domain.user.service.AuthService;
import com.readmate.global.error.BusinessException;
import com.readmate.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * check-then-act 경쟁 검증: 같은 이메일로 동시에 10건 가입하면
 * existsByEmail 사전 확인은 여러 요청이 동시에 통과할 수 있지만, uk_users_email 이 1건만 남긴다.
 * 실제 커밋이 필요하므로 @Transactional 을 쓰지 않고, 끝나면 직접 삭제한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class ConcurrentSignupTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        registry.add("spring.datasource.username", () -> envOr("DB_TEST_USERNAME", "shop"));
        registry.add("spring.datasource.password", () -> envOr("DB_TEST_PASSWORD", "shop1234"));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    @Autowired
    AuthService authService;
    @Autowired
    JdbcTemplate jdbc;

    private final String email = "race-" + System.nanoTime() + "@test.com";

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE email = ?", email);
    }

    @Test
    void 같은_이메일_동시_가입_10건_중_1건만_성공한다() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await(); // 모든 스레드가 준비된 뒤 동시에 출발
                try {
                    authService.signup(new SignupRequest(email, "password1!", "동시가입", null));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getErrorCode().name();
                }
            }));
        }
        ready.await();
        start.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(outcomes).filteredOn(ErrorCode.DUPLICATE_EMAIL.name()::equals).hasSize(threads - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email))
                .isEqualTo(1);
    }
}
