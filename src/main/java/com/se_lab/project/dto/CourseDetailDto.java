package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailDto extends BasePlaceDto {
    private String description;

    public CourseDetailDto(String title, String addr1, double mapy, double mapx, String thumbnailUrl, String description) {
        super(title, addr1, mapy, mapx, thumbnailUrl);
        this.description = description;
    }
}