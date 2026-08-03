package com.se_lab.project.repository;

import com.se_lab.project.entity.PilgrimageRoute;
import com.se_lab.project.entity.SavedPilgrimage;
import com.se_lab.project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedPilgrimageRepository extends JpaRepository<SavedPilgrimage, Long> {
    List<SavedPilgrimage> findByUserOrderBySavedAtDesc(User user);
    Optional<SavedPilgrimage> findByUserAndRoute(User user, PilgrimageRoute route);
    boolean existsByUserAndRoute(User user, PilgrimageRoute route);
}
