package com.se_lab.project.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "r2")
public class R2StorageService implements StorageService {

    private final S3Client s3Client;
    private final ImageUploadValidator imageUploadValidator;
    private final String bucket;
    private final String publicBaseUrl;
    private final String objectPrefix;

    public R2StorageService(
            S3Client s3Client,
            ImageUploadValidator imageUploadValidator,
            @Value("${storage.r2.bucket}") String bucket,
            @Value("${storage.r2.public-base-url}") String publicBaseUrl,
            @Value("${storage.r2.object-prefix:dev}") String objectPrefix) {
        this.s3Client = s3Client;
        this.imageUploadValidator = imageUploadValidator;
        this.bucket = requireSetting(bucket, "R2_BUCKET");
        this.publicBaseUrl = stripTrailingSlash(requireSetting(publicBaseUrl, "R2_PUBLIC_BASE_URL"));
        this.objectPrefix = stripSlashes(objectPrefix);
    }

    @Override
    public String store(MultipartFile file, String subDir) {
        ImageUploadValidator.ValidatedImage image = imageUploadValidator.validate(file);
        String safeSubDir = validateSubDir(subDir);
        String filename = UUID.randomUUID() + image.extension();
        String objectKey = joinKey(objectPrefix, safeSubDir, filename);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(image.contentType())
                .contentLength((long) image.content().length)
                .cacheControl("public, max-age=31536000, immutable")
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(image.content()));
            return publicBaseUrl + "/" + objectKey;
        } catch (SdkException e) {
            log.error("R2 파일 저장 실패: {}", e.getMessage());
            throw new StorageOperationException("이미지 저장소에 파일을 저장하지 못했습니다.", e);
        }
    }

    private String validateSubDir(String subDir) {
        if (subDir == null || !subDir.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("올바르지 않은 업로드 경로입니다.");
        }
        return subDir;
    }

    private String joinKey(String... parts) {
        return String.join("/", parts);
    }

    private String requireSetting(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentVariable + " 환경변수가 필요합니다.");
        }
        return value;
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String stripSlashes(String value) {
        if (!StringUtils.hasText(value)) return "dev";
        return value.replaceAll("^/+|/+$", "");
    }
}
