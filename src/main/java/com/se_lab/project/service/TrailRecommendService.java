package com.se_lab.project.service;

import com.se_lab.project.dto.TrailNearbyResponseDto;
import com.se_lab.project.entity.Trail;
import com.se_lab.project.repository.TrailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrailRecommendService {

    private final TrailRepository trailRepository;

    public List<TrailNearbyResponseDto> findNearbyTrails(
            double userLat,
            double userLng,
            int limit
    ) {

        Runtime runtime = Runtime.getRuntime();

        System.gc();

        long beforeUsed =
                runtime.totalMemory() - runtime.freeMemory();


        // =====================================================
        // 1. DB에서 가까운 Trail 조회
        //    → Trail 전체가 아니라 ID + 거리만 가져옴
        // =====================================================

        List<Object[]> distanceResults =
                trailRepository.findNearbyTrailDistances(
                        userLat,
                        userLng,
                        limit
                );


        if (distanceResults.isEmpty()) {
            return List.of();
        }


        // =====================================================
        // 2. 선택된 Trail ID 추출
        // =====================================================

        List<Long> trailIds =
                distanceResults.stream()
                        .map(row -> ((Number) row[0]).longValue())
                        .toList();


        // =====================================================
        // 3. 선택된 Trail 5개 + Point만 조회
        // =====================================================

        List<Trail> trails =
                trailRepository.findAllWithPointsByIds(trailIds);


        // =====================================================
        // 4. Trail ID → 가장 가까운 거리 매핑
        // =====================================================

        java.util.Map<Long, Double> distanceMap =
                distanceResults.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        row -> ((Number) row[0]).longValue(),
                                        row -> ((Number) row[4]).doubleValue()
                                )
                        );


        // =====================================================
        // 5. 메모리 측정
        // =====================================================

        System.gc();

        long afterUsed =
                runtime.totalMemory() - runtime.freeMemory();


        long totalPoints =
                trails.stream()
                        .mapToLong(trail -> trail.getPoints().size())
                        .sum();


        System.out.println("===== Trail 메모리 측정 =====");
        System.out.println("조회된 Trail 개수 = " + trails.size());
        System.out.println("조회된 TrailPoint 개수 = " + totalPoints);
        System.out.println(
                "조회 전 Heap 사용량 = "
                        + beforeUsed / 1024 / 1024
                        + " MB"
        );
        System.out.println(
                "조회 후 Heap 사용량 = "
                        + afterUsed / 1024 / 1024
                        + " MB"
        );
        System.out.println(
                "증가량 = "
                        + (afterUsed - beforeUsed) / 1024 / 1024
                        + " MB"
        );
        System.out.println("===========================");


        // =====================================================
        // 6. API 응답 생성
        // =====================================================

        return trails.stream()
                .sorted(
                        Comparator.comparing(
                                trail -> distanceMap.get(trail.getId())
                        )
                )
                .map(trail -> {

                    double nearestDistance =
                            distanceMap.get(trail.getId());

                    return TrailNearbyResponseDto.builder()
                            .courseName(trail.getCourseName())
                            .region(trail.getRegion())
                            .trailDistanceKm(trail.getDistance())
                            .nearestDistanceKm(
                                    Math.round(
                                            nearestDistance * 1000.0
                                    ) / 1000.0
                            )
                            .points(trail.getPoints())
                            .build();
                })
                .toList();
    }

}