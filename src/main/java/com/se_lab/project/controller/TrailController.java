package com.se_lab.project.controller;

import com.se_lab.project.dto.TrailNearbyResponseDto;
import com.se_lab.project.entity.Trail;
import com.se_lab.project.repository.TrailRepository;
import com.se_lab.project.service.TrailRecommendService;
import com.se_lab.project.service.TrailSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Trails", description = "둘레길 관련 API")
@RequestMapping("/api/v1/trails")
@RequiredArgsConstructor
public class TrailController {

    private final TrailRepository trailRepository;
    private final TrailSyncService trailSyncService;
    private final TrailRecommendService trailRecommendService;

    @Operation(summary = "둘레길 목록 조회")
    @GetMapping
    public List<Trail> getTrails(){

        return trailRepository.findAll();

    }

    @Tag(name = "Admin", description = "관리자용 API")
    @RestController
    @RequestMapping("/api/v1/admin")
    @RequiredArgsConstructor
    public class AdminController {

        @Operation(summary = "둘레길 데이터 동기화")
        @PostMapping("/trails/sync")
        public void syncTrails() {
            trailSyncService.syncTrails();
        }
    }

    @Operation(summary = "주변 둘레길 추천")
    @GetMapping("/nearby")
    public List<TrailNearbyResponseDto> getNearbyTrails(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") int limit
    ){

        return trailRecommendService.findNearbyTrails(
                lat,
                lng,
                limit
        );
    }

}