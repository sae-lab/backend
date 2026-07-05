package com.se_lab.project.controller;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.CourseDetailDto;
import com.se_lab.project.dto.HomeRecommendDto; // ⭐️ 추가해야 함!
import com.se_lab.project.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    // 1. 기존 코드: 전체 목록 조회 및 검색 (완벽함)
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

    // 2. 기존 코드: 루트 상세 보기 (완벽함)
    @GetMapping("/{id}/detail")
    public ResponseEntity<CourseDetailDto> getRouteDetail(@PathVariable String id) {
        return ResponseEntity.ok(routeService.getRouteDetail(id));
    }

    // 🚀 3. 새로 추가할 코드: 홈 화면 랜덤 추천 3개! (좌표 불필요, 난이도 포함)
    @GetMapping("/recommend/random")
    public ResponseEntity<List<HomeRecommendDto>> getRandomRecommendRoutes() {
        // RouteService에서 랜덤으로 3개를 뽑고 HomeRecommendDto로 변환해서 주는 로직 호출!
        List<HomeRecommendDto> randomRoutes = routeService.getRandomRecommendRoutes(3);
        return ResponseEntity.ok(randomRoutes);
    }
}