package com.se_lab.project.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TrailDto {

    // API 식별자
    private String routeId;          // routeIdx
    private String courseId;         // crsIdx

    // 기본 정보
    private String courseName;       // crsKorNm
    private String region;           // sigun

    // 코스 정보
    private double distance;         // crsDstnc (km)
    private int requiredMinutes;     // crsTotlRqrmHour (분)
    private int difficulty;          // crsLevel
    private String courseType;       // crsCycle

    // 설명
    private String summary;          // crsSummary

    // GPX
    private String gpxUrl;
    private List<GpxPointDto> coordinates;
}