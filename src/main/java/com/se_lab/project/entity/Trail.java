package com.se_lab.project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String courseId;

    private String courseName;

    private String region;

    private Double distance;

    private Integer requiredMinutes;

    private Integer difficulty;

    private String gpxUrl;


    // 시작점
    private Double startLat;

    private Double startLng;


    // 종료점
    private Double endLat;

    private Double endLng;


    @Builder.Default
    @OneToMany(
            mappedBy = "trail",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("sequence ASC")
    private List<TrailPoint> points = new ArrayList<>();


    public void addPoint(TrailPoint point) {
        points.add(point);
        point.setTrail(this);
    }
}