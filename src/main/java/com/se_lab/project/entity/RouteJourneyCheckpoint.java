package com.se_lab.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 여정 시작 시점의 스팟 스냅샷 하나. GPS가 이 좌표 근처(도장 반경 이내)로 오면 visited=true로 찍힌다.
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "route_journey_checkpoints")
public class RouteJourneyCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_id", nullable = false)
    private RouteJourney journey;

    @Column(nullable = false)
    private int sequenceOrder;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    private String photoUrl;

    @Setter
    @Builder.Default
    @Column(nullable = false)
    private boolean visited = false;

    @Setter
    private LocalDateTime visitedAt;
}
