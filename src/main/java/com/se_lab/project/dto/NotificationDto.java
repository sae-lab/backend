package com.se_lab.project.dto;

import com.se_lab.project.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationDto {

    private Long id;
    private NotificationType type;

    /// 공개용 표시 이름만 담는다 (실명 아님).
    private String actorName;
    private String actorProfileImageUrl;

    private Long routeId;
    private String routeTitle;

    /// 댓글/답글 알림일 때 미리보기. 좋아요 알림에서는 null.
    private String commentPreview;

    private boolean read;
    private LocalDateTime createdAt;
}
