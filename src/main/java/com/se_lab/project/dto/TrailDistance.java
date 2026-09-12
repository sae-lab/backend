package com.se_lab.project.dto;

import com.se_lab.project.entity.Trail;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TrailDistance {

    private Trail trail;

    private double nearestDistanceKm;

}