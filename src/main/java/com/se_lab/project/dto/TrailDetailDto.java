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
public class TrailDetailDto {
    private String title;
    private String addr1;
    private String contentId;
    private Coordinate startPoint;
    private int estimatedStayTime;
    private List<Coordinate> trailPath;
}
