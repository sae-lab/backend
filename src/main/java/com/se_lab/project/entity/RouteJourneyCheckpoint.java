package com.se_lab.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 여정의 스팟 하나. 앱이 폰 안에서 이 스팟 근처(도장 반경 이내)에 왔다고 판정해 알려주면 visited=true로 찍힌다.
// 사용자가 직접 찍은 게시물 지점은 내용을 그대로 두고, 관광지는 콘텐츠 ID만 두고 보여줄 때 조회한다.
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

    /// 한국관광공사 콘텐츠 ID. 관광지 체크포인트에만 있다.
    ///
    /// 공사 규정상 관광 데이터는 로컬에 저장하지 않으므로, 이 경우 이름·좌표·사진 칸은 빈 값이고
    /// 보여줄 때 이 ID로 다시 조회한다 (#58). 사용자가 직접 찍은 게시물 지점은 null이다.
    @Column(name = "content_id", length = 50)
    private String contentId;

    /// 관광공사 데이터를 실시간으로 조회해서 채워야 하는 체크포인트인지.
    public boolean isTourSpot() {
        return contentId != null && !contentId.isBlank();
    }

    @Setter
    @Builder.Default
    @Column(nullable = false)
    private boolean visited = false;

    @Setter
    private LocalDateTime visitedAt;
}
