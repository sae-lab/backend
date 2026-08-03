package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
import com.se_lab.project.dto.PilgrimageSegmentDto;
import com.se_lab.project.entity.PilgrimageRoute;
import com.se_lab.project.entity.PilgrimageSegment;
import com.se_lab.project.repository.PilgrimageRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PilgrimageServiceImpl implements PilgrimageService {

    private static final Logger logger = LoggerFactory.getLogger(PilgrimageServiceImpl.class);
    private static final int SPOTS_PER_SEGMENT = 5;
    private static final double WALKING_SPEED_KMH = 4.0;

    private final PilgrimageRouteRepository pilgrimageRouteRepository;
    private final TourApiService tourApiService;
    private final java.util.Random random = new java.util.Random();

    @Override
    public List<PilgrimageRouteSummaryDto> getAllRoutes() {
        return pilgrimageRouteRepository.findAll().stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    public PilgrimageRouteDetailDto getRouteDetail(Long id) {
        PilgrimageRoute route = pilgrimageRouteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("순례길을 찾을 수 없습니다: " + id));

        List<PilgrimageSegmentDto> segmentDtos = route.getSegments().stream()
                .map(this::toSegmentDto)
                .collect(Collectors.toList());

        return PilgrimageRouteDetailDto.builder()
                .id(route.getId())
                .name(route.getName())
                .description(route.getDescription())
                .totalDistanceKm(totalDistance(route))
                .totalEstimatedMinutes(totalMinutes(route))
                .segments(segmentDtos)
                .build();
    }

    @Override
    @Transactional
    public PilgrimageRouteSummaryDto generateRandomRoute() {
        List<GangwonCity> belt = PilgrimageCityData.ALL_BELTS.get(random.nextInt(PilgrimageCityData.ALL_BELTS.size()));

        int maxSegments = Math.min(4, belt.size() - 1);
        int segmentCount = 2 + random.nextInt(Math.max(1, maxSegments - 1));
        int maxStart = belt.size() - 1 - segmentCount;
        int start = random.nextInt(maxStart + 1);

        List<GangwonCity> chain = belt.subList(start, start + segmentCount + 1);

        PilgrimageRoute route = PilgrimageRoute.builder()
                .name(chain.get(0).name() + "-" + chain.get(chain.size() - 1).name() + " 자동 생성 순례길")
                .description(chain.stream().map(GangwonCity::name).collect(Collectors.joining(" → ")) + "를 잇는 자동 생성 구간 코스")
                .build();

        for (int i = 0; i < chain.size() - 1; i++) {
            GangwonCity from = chain.get(i);
            GangwonCity to = chain.get(i + 1);
            double distance = GeoUtils.distanceKm(from.lat(), from.lng(), to.lat(), to.lng());

            route.addSegment(PilgrimageSegment.builder()
                    .sequenceOrder(i + 1)
                    .fromCity(from.name())
                    .toCity(to.name())
                    .fromLat(from.lat()).fromLng(from.lng())
                    .toLat(to.lat()).toLng(to.lng())
                    .distanceKm(Math.round(distance * 10) / 10.0)
                    .difficulty(difficultyFromDistance(distance))
                    .estimatedMinutes((int) Math.round(distance / WALKING_SPEED_KMH * 60))
                    .build());
        }

        PilgrimageRoute saved = pilgrimageRouteRepository.save(route);
        return toSummaryDto(saved);
    }

    private String difficultyFromDistance(double km) {
        if (km < 15) return "쉬움";
        if (km < 30) return "보통";
        return "어려움";
    }

    private PilgrimageSegmentDto toSegmentDto(PilgrimageSegment segment) {
        return PilgrimageSegmentDto.builder()
                .sequenceOrder(segment.getSequenceOrder())
                .fromCity(segment.getFromCity())
                .toCity(segment.getToCity())
                .fromLat(segment.getFromLat())
                .fromLng(segment.getFromLng())
                .toLat(segment.getToLat())
                .toLng(segment.getToLng())
                .distanceKm(segment.getDistanceKm())
                .difficulty(segment.getDifficulty())
                .estimatedMinutes(segment.getEstimatedMinutes())
                .spots(findSpotsAlongSegment(segment))
                .build();
    }

    private List<BasePlaceDto> findSpotsAlongSegment(PilgrimageSegment segment) {
        try {
            List<BasePlaceDto> fromNearby = tourApiService.getNearbyPlaces(
                    String.valueOf(segment.getFromLng()), String.valueOf(segment.getFromLat()));
            List<BasePlaceDto> toNearby = tourApiService.getNearbyPlaces(
                    String.valueOf(segment.getToLng()), String.valueOf(segment.getToLat()));

            List<BasePlaceDto> merged = new java.util.ArrayList<>();
            merged.addAll(fromNearby == null ? Collections.emptyList() : fromNearby);
            merged.addAll(toNearby == null ? Collections.emptyList() : toNearby);

            return merged.stream()
                    .filter(distinctByTitle())
                    .limit(SPOTS_PER_SEGMENT)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("구간 스팟 조회 실패 ({} -> {}): {}", segment.getFromCity(), segment.getToCity(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private java.util.function.Predicate<BasePlaceDto> distinctByTitle() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        return place -> seen.add(place.getTitle());
    }

    private PilgrimageRouteSummaryDto toSummaryDto(PilgrimageRoute route) {
        List<PilgrimageSegment> segments = route.getSegments();

        String cityChain = segments.isEmpty() ? "" :
                segments.get(0).getFromCity() + segments.stream()
                        .map(s -> " → " + s.getToCity())
                        .collect(Collectors.joining());

        return PilgrimageRouteSummaryDto.builder()
                .id(route.getId())
                .name(route.getName())
                .description(route.getDescription())
                .cityChain(cityChain)
                .totalDistanceKm(totalDistance(route))
                .totalEstimatedMinutes(totalMinutes(route))
                .difficulty(hardestDifficulty(segments))
                .segmentCount(segments.size())
                .build();
    }

    private double totalDistance(PilgrimageRoute route) {
        double sum = route.getSegments().stream().mapToDouble(PilgrimageSegment::getDistanceKm).sum();
        return Math.round(sum * 10) / 10.0;
    }

    private int totalMinutes(PilgrimageRoute route) {
        return route.getSegments().stream().mapToInt(PilgrimageSegment::getEstimatedMinutes).sum();
    }

    private String hardestDifficulty(List<PilgrimageSegment> segments) {
        return segments.stream()
                .map(PilgrimageSegment::getDifficulty)
                .max((a, b) -> difficultyRank(a) - difficultyRank(b))
                .orElse("보통");
    }

    private int difficultyRank(String difficulty) {
        String d = difficulty == null ? "" : difficulty.toLowerCase(Locale.ROOT);
        if (d.contains("어려움")) return 3;
        if (d.contains("쉬움")) return 1;
        return 2; // 보통 / 그 외
    }
}
