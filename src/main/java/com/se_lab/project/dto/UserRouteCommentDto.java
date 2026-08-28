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
public class UserRouteCommentDto {
    private Long id;
    private String authorName;
    private String authorProfileImageUrl;
    private boolean mine;
    private String content;
    private LocalDateTime createdAt;
    private Long parentId;
    @Builder.Default
    private List<UserRouteCommentDto> replies = List.of();
}
