package com.se_lab.project.repository;

import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteScrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRouteScrapRepository extends JpaRepository<UserRouteScrap, Long> {
    long countByRoute(UserRoute route);
    Optional<UserRouteScrap> findByUserAndRoute(User user, UserRoute route);
    boolean existsByUserAndRoute(User user, UserRoute route);
    List<UserRouteScrap> findByUserOrderByScrapedAtDesc(User user);
}
