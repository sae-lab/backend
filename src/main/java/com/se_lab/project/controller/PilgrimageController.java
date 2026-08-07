package com.se_lab.project.controller;

import com.se_lab.project.constants.TourApiConstants;
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
    public ResponseEntity<PilgrimageRouteSummaryDto> generateRoute(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(pilgrimageService.generateRandomRoute(toContentTypeId(category)));
    }

    private String toContentTypeId(String category) {
        if (category == null) return null;
        return switch (category) {
            case "tourist_attraction" -> TourApiConstants.CONTENT_TYPE_TOURIST_ATTRACTION;
            case "culture" -> TourApiConstants.CONTENT_TYPE_CULTURE;
            case "festival" -> TourApiConstants.CONTENT_TYPE_FESTIVAL;
            case "leports" -> TourApiConstants.CONTENT_TYPE_LEPORTS;
            case "food" -> TourApiConstants.CONTENT_TYPE_FOOD;
            default -> null;
        };
    }
}
