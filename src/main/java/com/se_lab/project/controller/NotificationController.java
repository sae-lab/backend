package com.se_lab.project.controller;

import com.se_lab.project.global.AuthUtil;
import com.se_lab.project.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/// 내 게시물에 달린 좋아요·댓글 알림.
/// SecurityConfig의 anyRequest().authenticated()에 걸려 로그인 없이는 접근되지 않는다.
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> list() {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();
        return ResponseEntity.ok(notificationService.list(email));
    }

    /// 종 아이콘의 빨간 점에 쓴다. 목록 전체를 받아오지 않아도 되게 따로 둔다.
    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount() {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(email)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead() {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();
        notificationService.markAllRead(email);
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
    }
}
