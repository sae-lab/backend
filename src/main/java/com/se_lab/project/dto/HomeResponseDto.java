package com.se_lab.project.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class HomeResponseDto {
    private String username;
    private List<HomeRecommendDto> recommendedRoutes;
}