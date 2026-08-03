package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilgrimageRouteSummaryDto {
    private Long id;
    private String name;
    private String description;
    private String cityChain; // 예: "강릉 → 동해 → 삼척"
    private double totalDistanceKm;
    private int totalEstimatedMinutes;
    private String difficulty; // 구간 중 가장 어려운 난이도
    private int segmentCount;
}
