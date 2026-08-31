package com.se_lab.project.global;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

// 업로드 용량 초과처럼 컨트롤러 로직에 닿기 전에 서블릿/스프링 레벨에서 먼저 터지는
// 예외를, 사용자에게 그대로 보여줄 수 있는 메시지로 바꿔준다.
// (그 외 비즈니스 예외는 지금처럼 각 컨트롤러가 개별적으로 처리한다.)
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        log.warn("업로드 용량 초과: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "사진 용량이 너무 큽니다. 10MB 이하로 올려주세요."));
    }
}
