package com.se_lab.project.controller;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.CourseDetailDto;
import com.se_lab.project.dto.HomeRecommendDto;
import com.se_lab.project.service.RouteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "Operations related to routes")
public class RouteController {

    private final RouteService routeService;

    @GetMapping
    public ResponseEntity<List<BasePlaceDto>> getRoutes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (keyword != null && !keyword.isEmpty()) {
            return ResponseEntity.ok(routeService.searchRoutes(keyword));
        } else {
            return ResponseEntity.ok(routeService.getAllRoutes(category, page, size));
        }
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<CourseDetailDto> getRouteDetail(@PathVariable String id) {
        return ResponseEntity.ok(routeService.getRouteDetail(id));
    }

    @GetMapping("/recommend/random")
    public ResponseEntity<List<HomeRecommendDto>> getRandomRecommendRoutes() {
        List<HomeRecommendDto> randomRoutes = routeService.getRandomRecommendRoutes(3);
        return ResponseEntity.ok(randomRoutes);
    }
}
