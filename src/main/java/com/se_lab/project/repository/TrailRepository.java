package com.se_lab.project.repository;

import com.se_lab.project.entity.Trail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrailRepository extends JpaRepository<Trail, Long> {
    boolean existsByCourseId(String courseId);
}