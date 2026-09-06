package com.se_lab.project.service;

import com.se_lab.project.dto.NotificationDto;
import com.se_lab.project.entity.Notification;
import com.se_lab.project.entity.NotificationType;
import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteComment;
import com.se_lab.project.repository.NotificationRepository;
import com.se_lab.project.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/// 좋아요·댓글 알림.
///
/// 알림 생성은 좋아요/댓글 동작에 곁다리로 붙는 일이라, 여기서 나는 문제로
/// 본래 동작(좋아요가 눌리는 것)이 실패하면 안 된다. 그래서 호출하는 쪽에서
/// 이미 검증된 엔티티만 넘겨받고 여기서는 추가 조회를 하지 않는다.
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_ITEMS = 100;
    private static final int PREVIEW_LENGTH = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // ── 알림 생성 ────────────────────────────────────────────────────────

    /// 좋아요를 눌렀을 때. 같은 사람이 껐다 켰다 해도 하나만 남는다.
    @Transactional
    public void notifyLike(UserRoute route, User actor) {
        User recipient = route.getAuthor();
        if (isSelf(recipient, actor)) return;
        if (notificationRepository.existsByActorAndRouteAndType(actor, route, NotificationType.LIKE)) return;

        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(NotificationType.LIKE)
                .route(route)
                .build());
    }

    /// 좋아요를 취소했을 때. 누르지도 않은 좋아요가 알림함에 남지 않게 지운다.
    @Transactional
    public void removeLikeNotification(UserRoute route, User actor) {
        notificationRepository.deleteByActorAndRouteAndType(actor, route, NotificationType.LIKE);
    }

    /// 댓글/답글을 달았을 때.
    ///
    /// 답글이면 원댓글 작성자에게 REPLY를, 게시물 작성자에게 COMMENT를 보낸다.
    /// 둘이 같은 사람이면 REPLY 하나만 간다 (같은 일로 알림이 두 번 오면 안 된다).
    @Transactional
    public void notifyComment(UserRoute route, User actor, UserRouteComment comment) {
        UserRouteComment parent = comment.getParent();

        if (parent == null) {
            save(route.getAuthor(), actor, NotificationType.COMMENT, route, comment);
            return;
        }

        User parentAuthor = parent.getAuthor();
        save(parentAuthor, actor, NotificationType.REPLY, route, comment);

        // 답글이 달린 글의 주인에게도 알린다. 원댓글 작성자와 같은 사람이면 건너뛴다.
        User routeAuthor = route.getAuthor();
        if (!isSelf(routeAuthor, parentAuthor)) {
            save(routeAuthor, actor, NotificationType.COMMENT, route, comment);
        }
    }

    /// 게시물이 지워지면 딸린 알림도 정리한다. 남겨두면 눌렀을 때 없는 글로 간다.
    @Transactional
    public void removeForRoute(UserRoute route) {
        notificationRepository.deleteByRoute(route);
    }

    @Transactional
    public void removeForComment(UserRouteComment comment) {
        notificationRepository.deleteByComment(comment);
    }

    private void save(User recipient, User actor, NotificationType type, UserRoute route, UserRouteComment comment) {
        if (isSelf(recipient, actor)) return;
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .route(route)
                .comment(comment)
                .build());
    }

    /// 내가 내 글에 남긴 반응은 알리지 않는다.
    private boolean isSelf(User a, User b) {
        return a != null && b != null && a.getId().equals(b.getId());
    }

    // ── 조회 ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationDto> list(String userEmail) {
        User me = findUser(userEmail);
        return notificationRepository.findForRecipient(me, PageRequest.of(0, MAX_ITEMS))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userEmail) {
        return notificationRepository.countByRecipientAndIsReadFalse(findUser(userEmail));
    }

    @Transactional
    public void markAllRead(String userEmail) {
        notificationRepository.markAllReadFor(findUser(userEmail));
    }

    private NotificationDto toDto(Notification n) {
        UserRouteComment comment = n.getComment();
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                // 실명(getName)이 아니라 공개용 표시 이름만 내보낸다.
                .actorName(n.getActor().getDisplayName())
                .actorProfileImageUrl(n.getActor().getProfileImageUrl())
                .routeId(n.getRoute().getId())
                .routeTitle(n.getRoute().getTitle())
                .commentPreview(comment == null ? null : preview(comment.getContent()))
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private String preview(String content) {
        if (content == null) return null;
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "…";
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + email));
    }
}
