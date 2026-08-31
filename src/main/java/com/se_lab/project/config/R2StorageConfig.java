package com.se_lab.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "r2")
public class R2StorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client r2S3Client(
            @Value("${storage.r2.endpoint}") String endpoint,
            @Value("${storage.r2.access-key-id}") String accessKeyId,
            @Value("${storage.r2.secret-access-key}") String secretAccessKey) {
        requireSetting(endpoint, "R2_ENDPOINT");
        requireSetting(accessKeyId, "R2_ACCESS_KEY_ID");
        requireSetting(secretAccessKey, "R2_SECRET_ACCESS_KEY");

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    private void requireSetting(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentVariable + " 환경변수가 필요합니다.");
        }
    }
}
