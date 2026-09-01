package com.se_lab.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesValidatedImageUnderRequestedDirectory() throws Exception {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(10));
        LocalStorageService storage = new LocalStorageService(validator, tempDir.toString());
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};

        String url = storage.store(
                new MockMultipartFile("photo", "photo.exe", "application/octet-stream", jpeg),
                "profiles");

        assertThat(url).startsWith("/uploads/profiles/").endsWith(".jpg");
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.readAllBytes(tempDir.resolve("profiles").resolve(filename))).isEqualTo(jpeg);
    }
}
