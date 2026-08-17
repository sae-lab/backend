package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRouteDetailDto {
    private Long id;
    private String title;
    private String description;
    private String authorName;
    private boolean mine;
    private LocalDateTime createdAt;
    private List<UserRouteWaypointDto> waypoints;
    private long likeCount;
    private boolean likedByMe;
    private List<UserRouteCommentDto> comments;
}
