package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRouteSummaryDto {
    private Long id;
    private String title;
    private String description;
    private String authorName;
    private LocalDateTime createdAt;
    private String thumbnailUrl;
    private int waypointCount;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;
}
