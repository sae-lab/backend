package com.se_lab.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 사용자가 실제로 걷고 있는(또는 걸은) 순례길/산책길 여정.
// AI 순례길(PilgrimageRoute) 또는 게시판 게시물(UserRoute)을 선택해 추적을 시작하면
// 그 시점의 스팟들을 RouteJourneyCheckpoint로 스냅샷 떠서 "도장판"처럼 하나씩 방문 체크한다.
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "route_journeys")
public class RouteJourney {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // "AI_PILGRIMAGE"(PilgrimageRoute.id) 또는 "USER_ROUTE"(UserRoute.id)
    @Column(nullable = false)
    private String sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private String title;

    private Double totalDistanceKm;

    // "IN_PROGRESS" | "COMPLETED" | "ABANDONED"
    @Setter
    @Builder.Default
    @Column(nullable = false)
    private String status = "IN_PROGRESS";

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Setter
    private LocalDateTime completedAt;

    @Setter
    @Builder.Default
    @Column(nullable = false)
    private double walkedDistanceKm = 0;

    @Setter
    @Builder.Default
    @Column(nullable = false)
    private long elapsedSeconds = 0;

    @Setter
    private LocalDateTime lastPingAt;

    @Setter
    private Double lastLat;

    @Setter
    private Double lastLng;

    @Builder.Default
    @OneToMany(mappedBy = "journey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceOrder ASC")
    private List<RouteJourneyCheckpoint> checkpoints = new ArrayList<>();

    public void addCheckpoint(RouteJourneyCheckpoint checkpoint) {
        checkpoints.add(checkpoint);
        checkpoint.setJourney(this);
    }
}
