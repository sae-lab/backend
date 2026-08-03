package com.se_lab.project.controller;

import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
import com.se_lab.project.service.PilgrimageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pilgrimages")
@RequiredArgsConstructor
public class PilgrimageController {

    private final PilgrimageService pilgrimageService;

    @GetMapping
    public ResponseEntity<List<PilgrimageRouteSummaryDto>> getAllRoutes() {
        return ResponseEntity.ok(pilgrimageService.getAllRoutes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRouteDetail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(pilgrimageService.getRouteDetail(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<PilgrimageRouteSummaryDto> generateRoute() {
        return ResponseEntity.ok(pilgrimageService.generateRandomRoute());
    }
}
