package com.se_lab.project.repository;

import com.se_lab.project.entity.PilgrimageRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PilgrimageRouteRepository
        extends JpaRepository<PilgrimageRoute, Long> {

    boolean existsByIdentifier(String identifier);
}