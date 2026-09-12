package com.se_lab.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pilgrimage_routes")
public class PilgrimageRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String identifier;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    // 자동생성 시 선택한 관심 카테고리(Tour API contentTypeId). null이면 필터 없음.
    private String category;

    @Builder.Default
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceOrder ASC")
    private List<PilgrimageSegment> segments = new ArrayList<>();

    public void addSegment(PilgrimageSegment segment) {
        segments.add(segment);
        segment.setRoute(this);
    }
}
