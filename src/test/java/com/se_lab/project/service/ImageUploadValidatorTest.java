package com.se_lab.project.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadValidatorTest {

    private final ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(10));

    // 메타데이터를 지우려고 구조를 읽으므로, 시작 바이트만 맞는 가짜가 아니라 최소한의 JPEG를 쓴다.
    // SOI, SOS(스캔 데이터 2바이트), EOI
    private static final byte[] MINIMAL_JPEG = {
            (byte) 0xFF, (byte) 0xD8,
            (byte) 0xFF, (byte) 0xDA, 0x00, 0x02, 0x11, 0x22,
            (byte) 0xFF, (byte) 0xD9
    };

    @Test
    void acceptsJpegAndUsesDetectedType() {
        MockMultipartFile file = new MockMultipartFile("photo", "fake.png", "application/octet-stream", MINIMAL_JPEG);

        ImageUploadValidator.ValidatedImage result = validator.validate(file);

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo(".jpg");
        assertThat(result.content()).isEqualTo(MINIMAL_JPEG);
    }

    @Test
    void storedJpegHasNoLocationMetadata() {
        byte[] exifWithGps = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x14,
                'E', 'x', 'i', 'f', 0, 0, 'I', 'I', 42, 0, 8, 0, 0, 0, 'G', 'P', 'S', '!'
        };
        byte[] jpeg = new byte[2 + exifWithGps.length + MINIMAL_JPEG.length - 2];
        System.arraycopy(MINIMAL_JPEG, 0, jpeg, 0, 2);
        System.arraycopy(exifWithGps, 0, jpeg, 2, exifWithGps.length);
        System.arraycopy(MINIMAL_JPEG, 2, jpeg, 2 + exifWithGps.length, MINIMAL_JPEG.length - 2);

        ImageUploadValidator.ValidatedImage result = validator.validate(new MockMultipartFile("photo", jpeg));

        assertThat(result.content()).isEqualTo(MINIMAL_JPEG);
    }

    @Test
    void rejectsBrokenJpegRatherThanStoringItsMetadata() {
        byte[] broken = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("photo", broken)))
                .isInstanceOfSatisfying(ImageUploadException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
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
