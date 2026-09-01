package com.se_lab.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.legacy-upload-dir:}")
    private String legacyUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 새 공통 루트(user-routes, profiles, ...)를 먼저 조회한다.
        ResourceHandlerRegistration resourceHandler = registry.addResourceHandler("/uploads/**")
                .addResourceLocations(toFileLocation(uploadDir));

        // 기본 경로 변경 전 파일은 새 위치에 없을 때만 이전 루트에서 제공한다.
        if (StringUtils.hasText(legacyUploadDir)) {
            resourceHandler.addResourceLocations(toFileLocation(legacyUploadDir));
        }
    }

    private String toFileLocation(String directory) {
        return "file:" + Paths.get(directory).toAbsolutePath().normalize() + "/";
    }
}
