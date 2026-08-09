package com.se_lab.project.dto;

import com.se_lab.project.entity.TrailPoint;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TrailNearbyResponseDto {

    private String courseName;

    private String region;
    private Double trailDistanceKm;
    private Double nearestDistanceKm;

    private List<TrailPoint> points;

}