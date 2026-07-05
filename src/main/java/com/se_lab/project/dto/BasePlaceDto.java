package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BasePlaceDto {
    private String title;
    private String addr1;
    private double mapy;
    private double mapx;
    private String thumbnailUrl;
}