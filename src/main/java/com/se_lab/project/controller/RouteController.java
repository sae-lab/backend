package com.se_lab.project.controller;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.Coordinate;
import com.se_lab.project.dto.CourseDetailDto;
import com.se_lab.project.dto.HomeRecommendDto;
import com.se_lab.project.service.KakaoDirectionsService;
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
    private final KakaoDirectionsService kakaoDirectionsService;

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
        CourseDetailDto detail = routeService.getRouteDetail(id);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/recommend/random")
    public ResponseEntity<List<HomeRecommendDto>> getRandomRecommendRoutes() {
        List<HomeRecommendDto> randomRoutes = routeService.getRandomRecommendRoutes(3);
        return ResponseEntity.ok(randomRoutes);
    }

    @GetMapping("/optimal")
    public ResponseEntity<List<BasePlaceDto>> getOptimalRoute(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam int minutes) {

        return ResponseEntity.ok(
                routeService.getOptimalRoute(longitude, latitude, minutes)
        );
    }

    // 구간(현재위치→스팟1→스팟2...)별 실제 도보 경로 좌표. 프론트가 다리(leg)별로 호출해서 이어붙인다.
    @GetMapping("/path")
    public ResponseEntity<List<Coordinate>> getWalkPath(
            @RequestParam double fromLng,
            @RequestParam double fromLat,
            @RequestParam double toLng,
            @RequestParam double toLat) {
        return ResponseEntity.ok(kakaoDirectionsService.getRoutePath(fromLat, fromLng, toLat, toLng));
    }
}
