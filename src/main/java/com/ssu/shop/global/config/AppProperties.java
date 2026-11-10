package com.ssu.shop.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Admin admin, Upload upload) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Admin(String email, String password, String name) {
    }

    /** 이미지 저장 폴더 (상대 경로면 실행 위치 기준) */
    public record Upload(String dir) {
    }
}
