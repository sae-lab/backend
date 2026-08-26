package com.se_lab.project.controller;

import com.se_lab.project.dto.TrailNearbyResponseDto;
import com.se_lab.project.entity.Trail;
import com.se_lab.project.repository.TrailRepository;
import com.se_lab.project.service.TrailRecommendService;
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
    private final TrailRecommendService trailRecommendService;

    @Operation(summary = "둘레길 목록 조회")
    @GetMapping
    public List<Trail> getTrails(){

        return trailRepository.findAll();

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
