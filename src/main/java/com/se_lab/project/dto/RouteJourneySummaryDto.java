package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteJourneySummaryDto {
    private Long id;
    private String sourceType;
    private Long sourceId;
    private String title;
    private Double totalDistanceKm;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private double walkedDistanceKm;
    private long elapsedSeconds;
    private double completionRate;
}
