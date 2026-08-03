package com.se_lab.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pilgrimage_segments")
public class PilgrimageSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private PilgrimageRoute route;

    @Column(nullable = false)
    private int sequenceOrder;

    @Column(nullable = false)
    private String fromCity;

    @Column(nullable = false)
    private String toCity;

    @Column(nullable = false)
    private double fromLat;

    @Column(nullable = false)
    private double fromLng;

    @Column(nullable = false)
    private double toLat;

    @Column(nullable = false)
    private double toLng;

    @Column(nullable = false)
    private double distanceKm;

    @Column(nullable = false)
    private String difficulty;

    @Column(nullable = false)
    private int estimatedMinutes;
}
