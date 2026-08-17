package com.se_lab.project.repository;

import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRouteCommentRepository extends JpaRepository<UserRouteComment, Long> {
    List<UserRouteComment> findByRouteOrderByCreatedAtAsc(UserRoute route);
    long countByRoute(UserRoute route);
}
