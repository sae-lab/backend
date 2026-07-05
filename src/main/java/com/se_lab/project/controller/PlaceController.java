package com.se_lab.project.controller;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.service.TourApiService;
import org.springframework.web.bind.annotation.*;

import java.util.List; // ⭐️ List 임포트 추가

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final TourApiService tourApiService;

    public PlaceController(TourApiService tourApiService) {
        this.tourApiService = tourApiService;
    }

    // ⭐️ 기존 public String -> public List<PlaceDto> 로 변경!
    @GetMapping("/nearby")
    public List<BasePlaceDto> getNearbyPlaces(@RequestParam String mapX, @RequestParam String mapY) {
        return tourApiService.getNearbyPlaces(mapX, mapY);
    }
}