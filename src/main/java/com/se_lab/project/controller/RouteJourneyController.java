package com.se_lab.project.controller;

import com.se_lab.project.global.AuthUtil;
import com.se_lab.project.service.RouteJourneyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> startJourney(@RequestBody Map<String, String> body) {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        String sourceType = body.get("sourceType");
        Long sourceId;
        try {
            sourceId = Long.valueOf(body.get("sourceId"));
        } catch (NumberFormatException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "sourceId가 올바르지 않습니다."));
        }

        try {
            return ResponseEntity.ok(routeJourneyService.startJourney(email, sourceType, sourceId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveJourney() {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        var active = routeJourneyService.getActiveJourney(email);
        if (active == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(active);
    }

    @PostMapping("/{id}/ping")
    public ResponseEntity<?> ping(@PathVariable Long id, @RequestBody Map<String, Double> body) {
        String email = AuthUtil.requireLoggedIn();
        if (email == null) return unauthorized();

        Double lat = body.get("lat");
        Double lng = body.get("lng");
        if (lat == null || lng == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "lat/lng가 필요합니다."));
        }

        try {
            return ResponseEntity.ok(routeJourneyService.ping(email, id, lat, lng));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
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
