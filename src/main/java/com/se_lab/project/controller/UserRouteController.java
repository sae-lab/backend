package com.se_lab.project.controller;

import com.se_lab.project.dto.UserRouteCommentDto;
import com.se_lab.project.dto.UserRouteDetailDto;
import com.se_lab.project.dto.UserRouteSummaryDto;
import com.se_lab.project.global.AuthUtil;
import com.se_lab.project.service.UserRouteService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user-routes")
@RequiredArgsConstructor
public class UserRouteController {

    private final UserRouteService userRouteService;

    @GetMapping
    public ResponseEntity<List<UserRouteSummaryDto>> getAllRoutes(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(userRouteService.getAllRoutes(currentUserEmail(), type, sort));
    }

    @GetMapping("/mine")
    public ResponseEntity<?> getMyRoutes(@RequestParam(required = false) String type) {
        String email = requireLoggedIn();
        if (email == null) return unauthorized();
        return ResponseEntity.ok(userRouteService.getMyRoutes(email, type));
    }

    @GetMapping("/scraps")
    public ResponseEntity<?> getMyScraps(@RequestParam(required = false) String type) {
        String email = requireLoggedIn();
        if (email == null) return unauthorized();
        return ResponseEntity.ok(userRouteService.getMyScraps(email, type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRouteDetail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userRouteService.getRouteDetail(id, currentUserEmail()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 1번 웨이포인트에서 가장 가까운 순서대로 재정렬해 실제 도보 경로로 이어붙인 좌표들.
    @GetMapping("/{id}/walking-path")
    public ResponseEntity<?> getWalkingPath(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userRouteService.getWalkingPath(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> createRoute(@RequestBody Map<String, String> body) {
        Long id = userRouteService.createRoute(
                currentUserEmail(), body.get("title"), body.get("description"), body.get("routeType"));
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PostMapping("/from-pilgrimage/{pilgrimageRouteId}")
    public ResponseEntity<?> createFromPilgrimage(
            @PathVariable Long pilgrimageRouteId,
            @RequestBody(required = false) Map<String, Object> body) {
        String routeType = body != null && body.get("routeType") instanceof String type ? type : null;
        try {
            // contentIds가 없으면 모든 스팟을, 있으면 사용자가 고른 스팟만 그 순서대로 옮긴다.
            List<String> contentIds = contentIdsFrom(body);
            Long id = userRouteService.createFromPilgrimage(pilgrimageRouteId, currentUserEmail(), routeType, contentIds);
            return ResponseEntity.ok(Map.of("id", id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /// 요청 본문의 contentIds. 없으면 null, 목록이 아니면 IllegalArgumentException.
    private static List<String> contentIdsFrom(Map<String, Object> body) {
        Object value = body != null ? body.get("contentIds") : null;
        if (value == null) return null;
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("contentIds는 목록이어야 합니다.");
        }
        return list.stream().filter(item -> item != null).map(String::valueOf).toList();
    }

    @PostMapping(value = "/{id}/waypoints", consumes = "multipart/form-data")
    public ResponseEntity<?> addWaypoint(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam(required = false) String memo,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) MultipartFile photo) {
        try {
            userRouteService.addWaypoint(id, currentUserEmail(), title, memo, lat, lng, photo);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/waypoints/{sequenceOrder}")
    public ResponseEntity<?> deleteWaypoint(@PathVariable Long id, @PathVariable int sequenceOrder) {
        try {
            userRouteService.deleteWaypoint(id, currentUserEmail(), sequenceOrder);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long id) {
        try {
            boolean liked = userRouteService.toggleLike(id, currentUserEmail());
            return ResponseEntity.ok(Map.of("liked", liked));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/scrap")
    public ResponseEntity<?> toggleScrap(@PathVariable Long id) {
        try {
            boolean scrapped = userRouteService.toggleScrap(id, currentUserEmail());
            return ResponseEntity.ok(Map.of("scrapped", scrapped));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            Long parentId = null;
            String parentIdRaw = body.get("parentId");
            if (parentIdRaw != null && !parentIdRaw.isBlank()) {
                try {
                    parentId = Long.valueOf(parentIdRaw);
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest().body(Map.of("message", "parentId가 올바르지 않습니다."));
                }
            }
            UserRouteCommentDto comment = userRouteService.addComment(id, currentUserEmail(), body.get("content"), parentId);
            return ResponseEntity.ok(comment);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoute(@PathVariable Long id) {
        try {
            userRouteService.deleteRoute(id, currentUserEmail());
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId) {
        try {
            userRouteService.deleteComment(commentId, currentUserEmail());
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    private String currentUserEmail() {
        return AuthUtil.currentUserEmail();
    }

    private String requireLoggedIn() {
        return AuthUtil.requireLoggedIn();
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
    }
}
