package com.ssu.shop.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** "오늘", "이번 주" 계산용 시계. DB 시각과 같은 Asia/Seoul. 테스트에서는 고정 시계로 바꿔 끼운다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
