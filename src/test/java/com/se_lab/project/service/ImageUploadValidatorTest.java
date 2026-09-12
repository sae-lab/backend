package com.se_lab.project.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadValidatorTest {

    private final ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(10));

    @Test
    void acceptsJpegAndUsesDetectedType() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MockMultipartFile file = new MockMultipartFile("photo", "fake.png", "application/octet-stream", jpeg);

        ImageUploadValidator.ValidatedImage result = validator.validate(file);

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo(".jpg");
    }

    @Test
    void acceptsPngAndWebpSignatures() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertThat(validator.validate(new MockMultipartFile("photo", png)).contentType())
                .isEqualTo("image/png");
        assertThat(validator.validate(new MockMultipartFile("photo", webp)).contentType())
                .isEqualTo("image/webp");
    }

    @Test
    void rejectsEmptyAndUnsupportedFiles() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("photo", new byte[0])))
                .isInstanceOfSatisfying(ImageUploadException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("photo", "note.txt", "text/plain", "hello".getBytes())))
                .isInstanceOfSatisfying(ImageUploadException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsFilesLargerThanTenMegabytes() {
        byte[] oversized = new byte[(int) DataSize.ofMegabytes(10).toBytes() + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;

        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("photo", oversized)))
                .isInstanceOfSatisfying(ImageUploadException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }
}
