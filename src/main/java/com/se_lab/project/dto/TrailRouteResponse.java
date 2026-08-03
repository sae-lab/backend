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
public class TrailRouteResponse {
    private Coordinate startPoint;
    private boolean isFar;
    private List<Coordinate> trailPath;
    private TrailDetailDto trailDetail;
}
