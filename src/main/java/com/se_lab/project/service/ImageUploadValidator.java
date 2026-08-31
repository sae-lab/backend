package com.se_lab.project.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ImageUploadValidator {

    private static final int SIGNATURE_LENGTH = 12;

    private final DataSize maxFileSize;

    public ImageUploadValidator(
            @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageUploadException(HttpStatus.BAD_REQUEST, "업로드할 사진이 없습니다.");
        }
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new ImageUploadException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "이미지는 최대 " + maxFileSize.toMegabytes() + "MB까지 업로드할 수 있습니다.");
        }

        byte[] signature;
        try (InputStream input = file.getInputStream()) {
            signature = input.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException e) {
            throw new ImageUploadException(HttpStatus.BAD_REQUEST, "이미지 파일을 읽을 수 없습니다.");
        }

        if (isJpeg(signature)) {
            return new ValidatedImage("image/jpeg", ".jpg");
        }
        if (isPng(signature)) {
            return new ValidatedImage("image/png", ".png");
        }
        if (isWebp(signature)) {
            return new ValidatedImage("image/webp", ".webp");
        }

        throw new ImageUploadException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "JPEG, PNG, WebP 형식의 이미지만 업로드할 수 있습니다.");
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        int[] png = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < png.length) return false;
        for (int i = 0; i < png.length; i++) {
            if (unsigned(bytes[i]) != png[i]) return false;
        }
        return true;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    public record ValidatedImage(String contentType, String extension) {
    }
}
