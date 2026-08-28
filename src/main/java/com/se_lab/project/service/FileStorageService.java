package com.se_lab.project.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    // 업로드 파일들의 공통 루트 디렉터리 (예: "uploads"). 실제 파일은 그 아래 subDir별로 나뉜다.
    @Value("${file.upload-dir}")
    private String uploadDir;

    // 게시판 웨이포인트 사진 (기존 호출부와의 호환을 위한 기본 서브디렉터리)
    public String store(MultipartFile file) {
        return store(file, "user-routes");
    }

    public String store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 사진이 없습니다.");
        }

        try {
            Path uploadPath = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
            String filename = UUID.randomUUID() + ext;

            Path target = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), target);

            return "/uploads/" + subDir + "/" + filename;
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", e.getMessage());
            throw new RuntimeException("파일 저장에 실패했습니다.", e);
        }
    }
}
