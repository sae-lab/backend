package com.se_lab.project.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final ImageUploadValidator imageUploadValidator;
    private final Path uploadRoot;

    public LocalStorageService(
            ImageUploadValidator imageUploadValidator,
            @Value("${file.upload-dir}") String uploadDir) {
        this.imageUploadValidator = imageUploadValidator;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file, String subDir) {
        ImageUploadValidator.ValidatedImage image = imageUploadValidator.validate(file);
        String safeSubDir = validateSubDir(subDir);

        try {
            Path uploadPath = uploadRoot.resolve(safeSubDir).normalize();
            if (!uploadPath.startsWith(uploadRoot)) {
                throw new IllegalArgumentException("올바르지 않은 업로드 경로입니다.");
            }
            Files.createDirectories(uploadPath);

            String filename = UUID.randomUUID() + image.extension();
            Path target = uploadPath.resolve(filename).normalize();
            Files.write(target, image.content(), StandardOpenOption.CREATE_NEW);

            return "/uploads/" + safeSubDir + "/" + filename;
        } catch (IOException e) {
            log.error("로컬 파일 저장 실패: {}", e.getMessage());
            throw new StorageOperationException("파일 저장에 실패했습니다.", e);
        }
    }

    private String validateSubDir(String subDir) {
        if (subDir == null || !subDir.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("올바르지 않은 업로드 경로입니다.");
        }
        return subDir;
    }
}
