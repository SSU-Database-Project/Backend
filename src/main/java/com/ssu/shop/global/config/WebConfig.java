package com.ssu.shop.global.config;

import com.ssu.shop.global.file.ImageStorage;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * /images/** 를 두 곳에서 찾는다.
 *  1) 업로드 폴더(app.upload.dir) — 판매자가 올린 이미지 (git 에 올리지 않음)
 *  2) classpath:/static/images/ — 시연용 샘플 이미지 (저장소에 포함, 예: /images/samples/tee.jpg)
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDir = Path.of(appProperties.upload().dir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(ImageStorage.URL_PREFIX + "**")
                .addResourceLocations(uploadDir, "classpath:/static/images/");
    }
}
