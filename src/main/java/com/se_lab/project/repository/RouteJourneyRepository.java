package com.se_lab.project.repository;

import com.se_lab.project.entity.RouteJourney;
import com.se_lab.project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteJourneyRepository extends JpaRepository<RouteJourney, Long> {
    Optional<RouteJourney> findByUserAndStatus(User user, String status);

    List<RouteJourney> findByUserOrderByStartedAtDesc(User user);
}
