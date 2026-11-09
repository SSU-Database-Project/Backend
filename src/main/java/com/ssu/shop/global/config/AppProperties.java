package com.ssu.shop.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Admin admin) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Admin(String email, String password, String name) {
    }
}
