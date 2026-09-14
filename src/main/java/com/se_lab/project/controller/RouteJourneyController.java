package com.se_lab.project.controller;

import com.se_lab.project.global.AuthUtil;
import com.se_lab.project.service.RouteJourneyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
public class RouteJourneyController {

    private final RouteJourneyService routeJourneyService;

    @GetMapping("/trackable")
    public ResponseEntity<?> getTrackableRoutes() {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();
        return ResponseEntity.ok(routeJourneyService.getTrackableRoutes(email));
    }

    @PostMapping
    public ResponseEntity<?> startJourney(@RequestBody Map<String, Object> body) {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        String sourceType = body.get("sourceType") instanceof String type ? type : null;
        Long sourceId;
        try {
            sourceId = Long.valueOf(String.valueOf(body.get("sourceId")));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "sourceId가 올바르지 않습니다."));
        }

        try {
            // AI 순례길은 상세 화면에서 고른 스팟만 추적할 수 있다. 없으면 모든 스팟.
            List<String> contentIds = contentIdsFrom(body);
            return ResponseEntity.ok(routeJourneyService.startJourney(email, sourceType, sourceId, contentIds));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /// 요청 본문의 contentIds. 없으면 null, 목록이 아니면 IllegalArgumentException.
    private static List<String> contentIdsFrom(Map<String, Object> body) {
        Object value = body.get("contentIds");
        if (value == null) return null;
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("contentIds는 목록이어야 합니다.");
        }
        return list.stream().filter(item -> item != null).map(String::valueOf).toList();
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveJourney() {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        var active = routeJourneyService.getActiveJourney(email);
        if (active == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(active);
    }

    /// 앱이 판정한 스팟 도착. 사용자 좌표는 받지 않는다 (#57).
    @PostMapping("/{id}/checkpoints/{sequenceOrder}/visit")
    public ResponseEntity<?> visitCheckpoint(@PathVariable Long id, @PathVariable int sequenceOrder) {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        try {
            return ResponseEntity.ok(routeJourneyService.markCheckpointVisited(email, id, sequenceOrder));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        }
    }

    /// 앱이 계산한 누적 걸은 거리(km)·시간(초).
    @PutMapping("/{id}/progress")
    public ResponseEntity<?> saveProgress(@PathVariable Long id, @RequestBody Map<String, Number> body) {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        Number walkedDistanceKm = body.get("walkedDistanceKm");
        Number elapsedSeconds = body.get("elapsedSeconds");
        if (walkedDistanceKm == null || elapsedSeconds == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "walkedDistanceKm/elapsedSeconds가 필요합니다."));
        }

        try {
            routeJourneyService.saveProgress(email, id, walkedDistanceKm.doubleValue(), elapsedSeconds.longValue());
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<?> abandon(@PathVariable Long id) {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        try {
            return ResponseEntity.ok(routeJourneyService.abandonJourney(email, id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJourney(@PathVariable Long id) {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        try {
            routeJourneyService.deleteJourney(email, id);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJourney(@PathVariable Long id) {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        try {
            return ResponseEntity.ok(routeJourneyService.getJourney(email, id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory() {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();
        return ResponseEntity.ok(routeJourneyService.getHistory(email));
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
    }
}
