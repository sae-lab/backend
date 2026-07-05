package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.CourseDetailDto;
import com.se_lab.project.dto.HomeRecommendDto;
import java.util.List;

public interface RouteService {
    List<BasePlaceDto> getAllRoutes(String category, int page, int size);
    List<BasePlaceDto> searchRoutes(String keyword);
    CourseDetailDto getRouteDetail(String id);

    List<HomeRecommendDto> getRandomRecommendRoutes(int count);
}