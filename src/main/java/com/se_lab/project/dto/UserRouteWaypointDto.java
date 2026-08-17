package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRouteWaypointDto {
    private int sequenceOrder;
    private String title;
    private String memo;
    private double lat;
    private double lng;
    private String photoUrl;
}
