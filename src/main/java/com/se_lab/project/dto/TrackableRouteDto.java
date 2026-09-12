package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 여정 추적을 시작할 수 있는 후보 경로 하나(저장한 AI 순례길, 내가 올린/스크랩한 게시물).
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackableRouteDto {
    private String sourceType; // "AI_PILGRIMAGE" | "USER_ROUTE"
    private Long sourceId;
    private String title;
    private String description;
    private String routeType; // USER_ROUTE인 경우 "WALK"/"PILGRIMAGE", AI_PILGRIMAGE는 null
    private Double totalDistanceKm;
    private String thumbnailUrl;
    private int waypointCount;
}
