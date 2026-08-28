package com.se_lab.project.repository;

import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRouteLikeRepository extends JpaRepository<UserRouteLike, Long> {
    long countByRoute(UserRoute route);
    Optional<UserRouteLike> findByUserAndRoute(User user, UserRoute route);
    boolean existsByUserAndRoute(User user, UserRoute route);
    void deleteByRoute(UserRoute route);
}
