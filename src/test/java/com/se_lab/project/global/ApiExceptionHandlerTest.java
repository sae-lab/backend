package com.se_lab.project.global;

import com.se_lab.project.service.ImageUploadException;
import com.se_lab.project.service.StorageOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsPayloadTooLargeForImageLimitViolations() {
        ResponseEntity<Map<String, String>> response = handler.handleImageUpload(
                new ImageUploadException(HttpStatus.PAYLOAD_TOO_LARGE, "이미지는 최대 10MB까지 업로드할 수 있습니다."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("message", "이미지는 최대 10MB까지 업로드할 수 있습니다.");
    }

    @Test
    void convertsStorageErrors() {
        assertThat(handler.handleStorageOperation(
                new StorageOperationException("저장 실패", new RuntimeException())).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
