package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GpxPoint {

    private double latitude;
    private double longitude;
    private double elevation;

}