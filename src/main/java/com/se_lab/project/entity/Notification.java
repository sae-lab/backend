package com.se_lab.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/// 내 게시물에 누군가 좋아요를 누르거나 댓글을 달았을 때 쌓이는 알림.
///
/// 알림에 보이는 이름은 항상 [User.getDisplayName]을 쓴다. 실명이 새면
/// 익명 게시판의 의미가 없어진다.
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "notifications",
        indexes = @Index(name = "idx_notifications_recipient", columnList = "recipient_id, created_at")
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// 알림을 받는 사람 (보통 게시물 작성자).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /// 좋아요를 누르거나 댓글을 단 사람.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private UserRoute route;

    /// 댓글/답글 알림일 때만 채워진다. 좋아요 알림에서는 null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private UserRouteComment comment;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public void markRead() {
        this.isRead = true;
    }
}
