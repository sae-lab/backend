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
public class RouteJourneyCheckpointDto {
    private int sequenceOrder;
    private String title;
    private double lat;
    private double lng;
    private String photoUrl;
    private boolean visited;
    private LocalDateTime visitedAt;
}
