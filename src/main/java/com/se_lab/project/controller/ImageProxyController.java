package com.se_lab.project.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

// 프론트(Flutter Web)에서 투어 API 등 외부 이미지를 직접 Image.network로 불러오면
// Flutter Web의 기본 렌더러(CanvasKit)가 픽셀을 읽으려고 CORS를 요구하는데,
// 외부 이미지 호스트가 그런 헤더를 안 주는 경우 사진이 안 보인다.
// 그래서 우리 서버가 대신 받아와서(이미 CORS 허용된 우리 오리진으로) 내려준다.
@Slf4j
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageProxyController {

    // 열어줄 이미지 호스트만 화이트리스트로 제한 — 임의 URL을 다 받아오는 오픈 프록시가
    // 되지 않도록 한다.
    private static final List<String> ALLOWED_HOSTS = List.of("tong.visitkorea.or.kr");

    private final RestTemplate restTemplate;

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        if (uri.getHost() == null || !ALLOWED_HOSTS.contains(uri.getHost())
                || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            ResponseEntity<byte[]> upstream = restTemplate.getForEntity(uri, byte[].class);
            MediaType contentType = upstream.getHeaders().getContentType();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType != null ? contentType : MediaType.IMAGE_JPEG);
            headers.setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());

            return new ResponseEntity<>(upstream.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            log.warn("이미지 프록시 실패 [{}]: {}", url, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
