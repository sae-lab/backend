package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class HomeRecommendDto extends BasePlaceDto {
    private String difficulty;

    public HomeRecommendDto(String title, String addr1, double mapy, double mapx, String thumbnailUrl, String difficulty) {
        super(title, addr1, mapy, mapx, thumbnailUrl);
        this.difficulty = difficulty;
    }
}