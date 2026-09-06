package com.se_lab.project.repository;

import com.se_lab.project.entity.Notification;
import com.se_lab.project.entity.NotificationType;
import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /// 알림 목록. actor/route를 같이 끌어와 목록을 그릴 때 N+1이 나지 않게 한다.
    @Query("""
            select n from Notification n
            join fetch n.actor
            join fetch n.route
            left join fetch n.comment
            where n.recipient = :recipient
            order by n.createdAt desc
            """)
    List<Notification> findForRecipient(@Param("recipient") User recipient, Pageable pageable);

    long countByRecipientAndIsReadFalse(User recipient);

    /// 좋아요를 취소하면 알림도 지운다. 남겨두면 누르지도 않은 좋아요가
    /// 알림함에 계속 보인다.
    void deleteByActorAndRouteAndType(User actor, UserRoute route, NotificationType type);

    /// 같은 사람이 좋아요를 껐다 켰다 해도 알림이 쌓이지 않게 한다.
    boolean existsByActorAndRouteAndType(User actor, UserRoute route, NotificationType type);

    /// 게시물이나 댓글이 지워질 때 딸린 알림도 함께 정리한다.
    /// 남겨두면 눌렀을 때 없는 글로 가게 된다.
    void deleteByRoute(UserRoute route);

    void deleteByComment(UserRouteComment comment);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.recipient = :recipient and n.isRead = false")
    void markAllReadFor(@Param("recipient") User recipient);
}
