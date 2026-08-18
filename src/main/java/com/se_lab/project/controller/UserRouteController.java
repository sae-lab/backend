package com.se_lab.project.controller;

import com.se_lab.project.dto.UserRouteCommentDto;
import com.se_lab.project.dto.UserRouteDetailDto;
import com.se_lab.project.dto.UserRouteSummaryDto;
import com.se_lab.project.service.UserRouteService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @PostMapping
    public ResponseEntity<Map<String, Long>> createRoute(@RequestBody Map<String, String> body) {
        Long id = userRouteService.createRoute(
                currentUserEmail(), body.get("title"), body.get("description"), body.get("routeType"));
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PostMapping(value = "/{id}/waypoints", consumes = "multipart/form-data")
    public ResponseEntity<?> addWaypoint(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam(required = false) String memo,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam MultipartFile photo) {
        try {
            userRouteService.addWaypoint(id, currentUserEmail(), title, memo, lat, lng, photo);
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
            UserRouteCommentDto comment = userRouteService.addComment(id, currentUserEmail(), body.get("content"));
            return ResponseEntity.ok(comment);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
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
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private String requireLoggedIn() {
        String email = currentUserEmail();
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) return null;
        return email;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
    }
}
