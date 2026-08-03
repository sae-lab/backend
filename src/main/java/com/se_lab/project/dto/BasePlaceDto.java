package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BasePlaceDto {
    private String title;
    private String addr1;
    private double mapy;
    private double mapx;
    private String thumbnailUrl;
    private String contentId;
    private String contentTypeId;
    private String cat1;
    private String cat2;
    private String cat3;
    private int estimatedStayTime; // 추가

}