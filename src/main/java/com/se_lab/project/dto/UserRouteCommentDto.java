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
public class UserRouteCommentDto {
    private Long id;
    private String authorName;
    private boolean mine;
    private String content;
    private LocalDateTime createdAt;
}
