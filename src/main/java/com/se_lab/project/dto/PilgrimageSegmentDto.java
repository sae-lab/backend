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
public class PilgrimageSegmentDto {
    private int sequenceOrder;
    private String fromCity;
    private String toCity;
    private double fromLat;
    private double fromLng;
    private double toLat;
    private double toLng;
    private double distanceKm;
    private String difficulty;
    private int estimatedMinutes;
    private List<BasePlaceDto> spots;
    private List<Coordinate> path;
}
