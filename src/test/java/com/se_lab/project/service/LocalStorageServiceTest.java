package com.se_lab.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesValidatedImageUnderRequestedDirectory() throws Exception {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(10));
        LocalStorageService storage = new LocalStorageService(validator, tempDir.toString());
        // 저장 전에 메타데이터를 지우려고 JPEG 구조를 읽으므로 최소한의 올바른 JPEG를 쓴다.
        // 메타데이터가 없어 저장된 바이트는 그대로다.
        byte[] jpeg = {
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xDA, 0x00, 0x02, 0x01, 0x02,
                (byte) 0xFF, (byte) 0xD9
        };

        String url = storage.store(
                new MockMultipartFile("photo", "photo.exe", "application/octet-stream", jpeg),
                "profiles");

        assertThat(url).startsWith("/uploads/profiles/").endsWith(".jpg");
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.readAllBytes(tempDir.resolve("profiles").resolve(filename))).isEqualTo(jpeg);
    }

    @Test
    void storesImageWhenRequestStreamCanOnlyBeReadOnce() {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(10));
        LocalStorageService storage = new LocalStorageService(validator, tempDir.toString());
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        assertThatCode(() -> storage.store(new SingleReadMultipartFile(png), "user-routes"))
                .doesNotThrowAnyException();
    }
}
