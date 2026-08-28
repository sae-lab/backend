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
@Table(name = "user_route_waypoints")
public class UserRouteWaypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private UserRoute route;

    @Column(nullable = false)
    private int sequenceOrder;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String memo;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    // 로컬 디스크에 저장된 업로드 사진의 상대경로(예: /uploads/user-routes/xxx.jpg) 또는
    // AI 순례길에서 게시물로 변환된 경우 외부(Tour API) 절대 URL. null 허용 — 사진이 없을 수도 있다.
    private String photoUrl;
}
