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

    // 사용자가 올린 사진의 경로. null 허용 — 사진이 없을 수도 있다.
    // 관광지 웨이포인트(contentId가 있는 것)는 사진을 저장하지 않고 보여줄 때 조회한다.
    private String photoUrl;

    /// 한국관광공사 OpenAPI의 콘텐츠 ID. AI 순례길을 게시물로 옮긴 웨이포인트에만 있다.
    ///
    /// 공사 규정상 관광 데이터는 로컬에 저장하지 않고 실시간으로 호출해야 한다.
    /// 그래서 이 경우 제목·주소·좌표·사진은 저장하지 않고(NOT NULL 칸은 빈 값),
    /// 보여줄 때마다 이 ID로 상세 정보를 다시 받아온다. 사용자가 직접 찍은 웨이포인트는 null이다.
    @Column(name = "content_id", length = 50)
    private String contentId;

    /// 관광공사 데이터를 실시간으로 조회해서 채워야 하는 웨이포인트인지.
    public boolean isTourSpot() {
        return contentId != null && !contentId.isBlank();
    }

    /// 중간 웨이포인트를 지운 뒤 남은 것들의 번호를 1부터 다시 매길 때 쓴다.
    /// 안 그러면 화면에 1, 3, 4처럼 빠진 번호가 그대로 보인다.
    public void renumber(int sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }
}
