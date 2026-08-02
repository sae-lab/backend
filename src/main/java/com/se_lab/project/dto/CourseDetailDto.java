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

    public CourseDetailDto(String title, String addr1, double mapy, double mapx, String thumbnailUrl, String contentId, String description) {
        super(
                title,
                addr1,
                mapy,
                mapx,
                thumbnailUrl,
                contentId,
                null, // contentTypeId
                null, // cat1
                null, // cat2
                null, // cat3
                0    // estimatedStayTime
        );
        this.description = description;
    }
}