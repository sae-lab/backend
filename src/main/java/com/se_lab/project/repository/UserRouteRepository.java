package com.se_lab.project.repository;

import com.se_lab.project.entity.UserRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRouteRepository extends JpaRepository<UserRoute, Long> {
    List<UserRoute> findAllByOrderByCreatedAtDesc();
}
