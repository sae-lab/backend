package com.se_lab.project.controller;

import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
import com.se_lab.project.service.SavedPilgrimageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/saved-pilgrimages")
@RequiredArgsConstructor
public class SavedPilgrimageController {

    private final SavedPilgrimageService savedPilgrimageService;

    @GetMapping
    public ResponseEntity<List<PilgrimageRouteSummaryDto>> getSavedRoutes() {
        return ResponseEntity.ok(savedPilgrimageService.getSavedRoutes(currentUserEmail()));
    }

    @PostMapping("/{routeId}")
    public ResponseEntity<?> saveRoute(@PathVariable Long routeId) {
        try {
            boolean created = savedPilgrimageService.saveRoute(currentUserEmail(), routeId);
            return ResponseEntity.ok(Map.of(
                    "saved", true,
                    "alreadySaved", !created
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{routeId}")
    public ResponseEntity<?> unsaveRoute(@PathVariable Long routeId) {
        try {
            boolean removed = savedPilgrimageService.unsaveRoute(currentUserEmail(), routeId);
            return ResponseEntity.ok(Map.of("saved", false, "removed", removed));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
