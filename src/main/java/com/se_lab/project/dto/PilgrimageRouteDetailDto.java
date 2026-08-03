package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilgrimageRouteDetailDto {
    private Long id;
    private String name;
    private String description;
    private double totalDistanceKm;
    private int totalEstimatedMinutes;
    private List<PilgrimageSegmentDto> segments;
}
