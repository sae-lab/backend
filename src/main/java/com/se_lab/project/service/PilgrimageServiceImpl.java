package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.Coordinate;
import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
import com.se_lab.project.dto.PilgrimageSegmentDto;
import com.se_lab.project.entity.PilgrimageRoute;
import com.se_lab.project.entity.PilgrimageSegment;
import com.se_lab.project.global.KoreanParticle;
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
    private static final int WAYPOINTS_PER_SEGMENT = 10;
    private static final double WALKING_SPEED_KMH = 4.0;

    /// 기준 경로에서 이만큼 넘게 떨어진 곳은 경유 스팟으로 치지 않는다.
    /// Tour API를 반경 10km로 긁어오다 보니 경로와 무관한 곳까지 섞여 들어왔다.
    private static final double MAX_SPOT_DETOUR_KM = 3.0;

    /// 경로를 다시 그릴 때 실제로 들를 스팟 수. 다리마다 외부 API를 부르므로
    /// 늘릴수록 상세 조회가 느려진다.
    private static final int MAX_ROUTED_SPOTS = 6;

    private final PilgrimageRouteRepository pilgrimageRouteRepository;
    private final TourApiService tourApiService;
    private final KakaoDirectionsService kakaoDirectionsService;
    private final OsrmWalkingDirectionsService osrmWalkingDirectionsService;
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
                .map(segment -> toSegmentDto(segment, route.getCategory()))
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
    public PilgrimageRouteSummaryDto generateRandomRoute(String category) {
        List<GangwonCity> belt = PilgrimageCityData.ALL_BELTS.get(random.nextInt(PilgrimageCityData.ALL_BELTS.size()));

        int maxSegments = Math.min(4, belt.size() - 1);
        int segmentCount = 2 + random.nextInt(Math.max(1, maxSegments - 1));
        int maxStart = belt.size() - 1 - segmentCount;
        int start = random.nextInt(maxStart + 1);

        List<GangwonCity> chain = belt.subList(start, start + segmentCount + 1);

        PilgrimageRoute route = PilgrimageRoute.builder()
                .name(chain.get(0).name() + "-" + chain.get(chain.size() - 1).name() + " 자동 생성 순례길")
                // 마지막 도시 받침에 따라 을/를이 갈린다. 하나로 고정하면 "삼척를"이 된다.
                .description(chain.stream().map(GangwonCity::name).collect(Collectors.joining(" → "))
                        + KoreanParticle.objective(chain.get(chain.size() - 1).name())
                        + " 잇는 자동 생성 구간 코스")
                .category(category)
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

    private PilgrimageSegmentDto toSegmentDto(PilgrimageSegment segment, String category) {
        // 1) 스팟을 찾기 위한 기준선. 도시 A -> B를 잇는 경로다.
        List<Coordinate> referencePath = routePath(segment);

        // 2) 기준선에서 멀리 떨어진 곳은 걸러낸다. 반경 10km로 긁어오다 보니
        //    경로와 상관없는 곳까지 "경유 스팟"으로 들어왔었다.
        List<BasePlaceDto> spots = findSpotsAlongSegment(segment, category, referencePath).stream()
                .filter(spot -> distanceToPathKm(spot, referencePath) <= MAX_SPOT_DETOUR_KM)
                .collect(Collectors.toList());

        // 3) 출발지에서 도착지 방향으로 지나는 순서대로 줄 세운다.
        spots = orderAlongPath(spots, referencePath);

        // 4) 그 스팟들을 실제로 지나는 경로를 다시 그린다. 이걸 안 하면 선은
        //    도시끼리만 잇고 스팟은 선 밖에 떠 있어서, 경유지처럼 보이지 않는다.
        List<Coordinate> path = routeThroughSpots(segment, spots, referencePath);

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
                .spots(spots)
                .path(path)
                .build();
    }

    /// 스팟이 기준 경로에서 얼마나 벗어나 있는지. 경로 위 점들과의 최단 거리로 잰다.
    private double distanceToPathKm(BasePlaceDto spot, List<Coordinate> path) {
        double min = Double.MAX_VALUE;
        for (Coordinate point : path) {
            double d = GeoUtils.distanceKm(spot.getLatitude(), spot.getLongitude(), point.getLat(), point.getLng());
            if (d < min) min = d;
        }
        return min;
    }

    /// 출발지에서 도착지로 가면서 만나는 순서대로 스팟을 정렬한다.
    ///
    /// 경로 위에서 가장 가까운 점이 몇 번째인지를 기준으로 삼는다. 이 순서가
    /// 곧 경로를 다시 그릴 때 들르는 순서가 되므로, 뒤죽박죽이면 길이 지그재그가 된다.
    private List<BasePlaceDto> orderAlongPath(List<BasePlaceDto> spots, List<Coordinate> path) {
        if (spots.size() < 2 || path.isEmpty()) return spots;

        return spots.stream()
                .sorted(java.util.Comparator.comparingInt(spot -> nearestPathIndex(spot, path)))
                .collect(Collectors.toList());
    }

    private int nearestPathIndex(BasePlaceDto spot, List<Coordinate> path) {
        int bestIndex = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            Coordinate point = path.get(i);
            double d = GeoUtils.distanceKm(spot.getLatitude(), spot.getLongitude(), point.getLat(), point.getLng());
            if (d < best) {
                best = d;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /// 출발지 -> 스팟들 -> 도착지를 차례로 이어 실제 도보 경로를 만든다.
    ///
    /// 다리(leg)마다 외부 경로 API를 부르므로 스팟이 많으면 그만큼 느려진다.
    /// 그래서 경유할 스팟 수를 제한하고, 넘치면 기준 경로를 그대로 쓴다.
    /// 한 다리라도 실패하면 그 구간만 직선으로 잇는다.
    private List<Coordinate> routeThroughSpots(PilgrimageSegment segment,
                                               List<BasePlaceDto> spots,
                                               List<Coordinate> referencePath) {
        if (spots.isEmpty()) return referencePath;

        List<BasePlaceDto> viaSpots = spots.size() > MAX_ROUTED_SPOTS
                ? spots.subList(0, MAX_ROUTED_SPOTS)
                : spots;

        List<Coordinate> stops = new java.util.ArrayList<>();
        stops.add(Coordinate.builder().lat(segment.getFromLat()).lng(segment.getFromLng()).build());
        viaSpots.forEach(s -> stops.add(
                Coordinate.builder().lat(s.getLatitude()).lng(s.getLongitude()).build()));
        stops.add(Coordinate.builder().lat(segment.getToLat()).lng(segment.getToLng()).build());

        List<Coordinate> path = new java.util.ArrayList<>();
        for (int i = 0; i < stops.size() - 1; i++) {
            Coordinate from = stops.get(i);
            Coordinate to = stops.get(i + 1);

            List<Coordinate> leg = osrmWalkingDirectionsService.getWalkingPath(
                    from.getLat(), from.getLng(), to.getLat(), to.getLng());

            if (leg.isEmpty()) {
                path.add(from);
                path.add(to);
            } else {
                path.addAll(leg);
            }
        }
        return path;
    }

    private List<Coordinate> routePath(PilgrimageSegment segment) {
        // 1순위: OSRM 도보 프로필(실제 걷는 경로). 2순위: 카카오 자동차 경로.
        // 3순위: 직선. 거리/난이도/소요시간 계산에는 어느 쪽이든 영향 없음(직선거리 기반 유지).
        List<Coordinate> path = osrmWalkingDirectionsService.getWalkingPath(
                segment.getFromLat(), segment.getFromLng(), segment.getToLat(), segment.getToLng());
        if (!path.isEmpty()) return path;

        path = kakaoDirectionsService.getRoutePath(
                segment.getFromLat(), segment.getFromLng(), segment.getToLat(), segment.getToLng());
        if (!path.isEmpty()) return path;

        return List.of(
                Coordinate.builder().lat(segment.getFromLat()).lng(segment.getFromLng()).build(),
                Coordinate.builder().lat(segment.getToLat()).lng(segment.getToLng()).build()
        );
    }

    // 경로 중간중간(25%, 50%, 75% 지점)도 훑어서, 양 끝 도시에만 몰리지 않고
    // 구간 전체에 걸쳐 웨이포인트가 나오도록 한다.
    private static final double[] WAYPOINT_SAMPLE_RATIOS = {0.25, 0.5, 0.75};

    private List<Coordinate> sampleWaypoints(List<Coordinate> path) {
        if (path.size() < 3) return Collections.emptyList();

        List<Coordinate> samples = new java.util.ArrayList<>();
        for (double ratio : WAYPOINT_SAMPLE_RATIOS) {
            int index = (int) Math.round((path.size() - 1) * ratio);
            samples.add(path.get(index));
        }
        return samples;
    }

    private List<BasePlaceDto> findSpotsAlongSegment(PilgrimageSegment segment, String category, List<Coordinate> path) {
        try {
            List<Coordinate> searchPoints = new java.util.ArrayList<>();
            searchPoints.add(Coordinate.builder().lat(segment.getFromLat()).lng(segment.getFromLng()).build());
            searchPoints.addAll(sampleWaypoints(path));
            searchPoints.add(Coordinate.builder().lat(segment.getToLat()).lng(segment.getToLng()).build());

            List<BasePlaceDto> merged = new java.util.ArrayList<>();
            for (Coordinate point : searchPoints) {
                List<BasePlaceDto> nearby = tourApiService.getNearbyPlaces(
                        String.valueOf(point.getLng()), String.valueOf(point.getLat()), category);
                if (nearby != null) merged.addAll(nearby);
            }

            return merged.stream()
                    .filter(distinctByTitle())
                    .limit(WAYPOINTS_PER_SEGMENT)
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
