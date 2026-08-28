package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteJourneyDetailDto {
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
    private double completionRate; // 0.0 ~ 1.0, 방문한 체크포인트 비율
    private int visitedCheckpointCount;
    private int totalCheckpointCount;
    private List<RouteJourneyCheckpointDto> checkpoints;
}
