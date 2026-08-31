package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
import com.se_lab.project.dto.PilgrimageSegmentDto;
import com.se_lab.project.dto.RouteJourneyCheckpointDto;
import com.se_lab.project.dto.RouteJourneyDetailDto;
import com.se_lab.project.dto.RouteJourneySummaryDto;
import com.se_lab.project.dto.TrackableRouteDto;
import com.se_lab.project.entity.RouteJourney;
import com.se_lab.project.entity.RouteJourneyCheckpoint;
import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteWaypoint;
import com.se_lab.project.repository.RouteJourneyRepository;
import com.se_lab.project.repository.UserRepository;
import com.se_lab.project.repository.UserRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteJourneyServiceImpl implements RouteJourneyService {

    // 이 반경(km) 이내로 GPS가 들어오면 그 스팟을 "방문"으로 도장 찍는다.
    private static final double CHECKPOINT_RADIUS_KM = 0.1; // 100m
    // 한 ping과 다음 ping 사이 간격이 이보다 길면(앱이 백그라운드에 오래 있었던 경우 등)
    // 그 시간은 걷기 시간에 합산하지 않는다.
    private static final long MAX_PING_GAP_SECONDS = 300; // 5분
    // 한 ping 사이의 이동 거리가 이보다 크면 GPS 튐으로 보고 이동거리에 반영하지 않는다.
    private static final double MAX_PING_JUMP_KM = 1.0;

    private final RouteJourneyRepository routeJourneyRepository;
    private final UserRepository userRepository;
    private final UserRouteRepository userRouteRepository;
    private final SavedPilgrimageService savedPilgrimageService;
    private final PilgrimageService pilgrimageService;

    @Override
    public List<TrackableRouteDto> getTrackableRoutes(String userEmail) {
        // 여정 선택 목록에는 저장한 AI 순례길만 노출한다. 게시물(USER_ROUTE)은 목록에서 고르는 대신
        // 게시물 상세 화면에서 바로 "여행으로 추가"해 startJourney를 직접 호출하는 방식으로 뺐다.
        List<TrackableRouteDto> result = new ArrayList<>();

        for (PilgrimageRouteSummaryDto p : savedPilgrimageService.getSavedRoutes(userEmail)) {
            result.add(TrackableRouteDto.builder()
                    .sourceType("AI_PILGRIMAGE")
                    .sourceId(p.getId())
                    .title(p.getName())
                    .description(p.getDescription())
                    .routeType(null)
                    .totalDistanceKm(p.getTotalDistanceKm())
                    .thumbnailUrl(null)
                    .waypointCount(p.getSegmentCount())
                    .build());
        }

        return result;
    }

    @Override
    @Transactional
    public RouteJourneyDetailDto startJourney(String userEmail, String sourceType, Long sourceId) {
        User user = findUser(userEmail);

        if (routeJourneyRepository.findByUserAndStatus(user, "IN_PROGRESS").isPresent()) {
            throw new IllegalStateException("이미 진행 중인 여정이 있습니다. 먼저 종료해주세요.");
        }

        RouteJourney journey;
        if ("AI_PILGRIMAGE".equals(sourceType)) {
            journey = buildFromPilgrimage(user, sourceId);
        } else if ("USER_ROUTE".equals(sourceType)) {
            journey = buildFromUserRoute(user, sourceId);
        } else {
            throw new IllegalArgumentException("지원하지 않는 경로 유형입니다: " + sourceType);
        }

        if (journey.getCheckpoints().isEmpty()) {
            throw new IllegalStateException("이 경로에는 추적할 스팟이 없습니다.");
        }

        RouteJourney saved = routeJourneyRepository.save(journey);
        return toDetailDto(saved);
    }

    private RouteJourney buildFromPilgrimage(User user, Long pilgrimageRouteId) {
        PilgrimageRouteDetailDto pilgrimage = pilgrimageService.getRouteDetail(pilgrimageRouteId);

        RouteJourney journey = RouteJourney.builder()
                .user(user)
                .sourceType("AI_PILGRIMAGE")
                .sourceId(pilgrimageRouteId)
                .title(pilgrimage.getName())
                .totalDistanceKm(pilgrimage.getTotalDistanceKm())
                .build();

        int sequence = 1;
        for (PilgrimageSegmentDto segment : pilgrimage.getSegments()) {
            for (BasePlaceDto spot : segment.getSpots()) {
                journey.addCheckpoint(RouteJourneyCheckpoint.builder()
                        .sequenceOrder(sequence++)
                        .title(spot.getTitle())
                        .lat(spot.getLatitude())
                        .lng(spot.getLongitude())
                        .photoUrl(spot.getThumbnailUrl())
                        .build());
            }
        }
        return journey;
    }

    private RouteJourney buildFromUserRoute(User user, Long userRouteId) {
        UserRoute route = userRouteRepository.findById(userRouteId)
                .orElseThrow(() -> new EntityNotFoundException("게시물을 찾을 수 없습니다: " + userRouteId));

        RouteJourney journey = RouteJourney.builder()
                .user(user)
                .sourceType("USER_ROUTE")
                .sourceId(userRouteId)
                .title(route.getTitle())
                .totalDistanceKm(estimatePathDistanceKm(route.getWaypoints()))
                .build();

        int sequence = 1;
        for (UserRouteWaypoint wp : route.getWaypoints()) {
            journey.addCheckpoint(RouteJourneyCheckpoint.builder()
                    .sequenceOrder(sequence++)
                    .title(wp.getTitle())
                    .lat(wp.getLat())
                    .lng(wp.getLng())
                    .photoUrl(wp.getPhotoUrl())
                    .build());
        }
        return journey;
    }

    private Double estimatePathDistanceKm(List<UserRouteWaypoint> waypoints) {
        if (waypoints.size() < 2) return null;
        double total = 0;
        for (int i = 1; i < waypoints.size(); i++) {
            UserRouteWaypoint prev = waypoints.get(i - 1);
            UserRouteWaypoint cur = waypoints.get(i);
            total += GeoUtils.distanceKm(prev.getLat(), prev.getLng(), cur.getLat(), cur.getLng());
        }
        return Math.round(total * 10) / 10.0;
    }

    @Override
    public RouteJourneyDetailDto getActiveJourney(String userEmail) {
        User user = findUser(userEmail);
        return routeJourneyRepository.findByUserAndStatus(user, "IN_PROGRESS")
                .map(this::toDetailDto)
                .orElse(null);
    }

    @Override
    @Transactional
    public RouteJourneyDetailDto ping(String userEmail, Long journeyId, double lat, double lng) {
        User user = findUser(userEmail);
        RouteJourney journey = findOwnedJourney(journeyId, user);

        if (!"IN_PROGRESS".equals(journey.getStatus())) {
            throw new IllegalStateException("이미 종료된 여정입니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        if (journey.getLastLat() != null && journey.getLastLng() != null && journey.getLastPingAt() != null) {
            double deltaKm = GeoUtils.distanceKm(journey.getLastLat(), journey.getLastLng(), lat, lng);
            long deltaSeconds = Duration.between(journey.getLastPingAt(), now).getSeconds();

            if (deltaKm <= MAX_PING_JUMP_KM) {
                journey.setWalkedDistanceKm(journey.getWalkedDistanceKm() + deltaKm);
            }
            if (deltaSeconds > 0 && deltaSeconds <= MAX_PING_GAP_SECONDS) {
                journey.setElapsedSeconds(journey.getElapsedSeconds() + deltaSeconds);
            }
        }

        journey.setLastLat(lat);
        journey.setLastLng(lng);
        journey.setLastPingAt(now);

        for (RouteJourneyCheckpoint checkpoint : journey.getCheckpoints()) {
            if (checkpoint.isVisited()) continue;
            double distanceKm = GeoUtils.distanceKm(lat, lng, checkpoint.getLat(), checkpoint.getLng());
            if (distanceKm <= CHECKPOINT_RADIUS_KM) {
                checkpoint.setVisited(true);
                checkpoint.setVisitedAt(now);
            }
        }

        boolean allVisited = journey.getCheckpoints().stream().allMatch(RouteJourneyCheckpoint::isVisited);
        if (allVisited) {
            journey.setStatus("COMPLETED");
            journey.setCompletedAt(now);
        }

        return toDetailDto(journey);
    }

    @Override
    @Transactional
    public RouteJourneyDetailDto abandonJourney(String userEmail, Long journeyId) {
        User user = findUser(userEmail);
        RouteJourney journey = findOwnedJourney(journeyId, user);

        if (!"IN_PROGRESS".equals(journey.getStatus())) {
            throw new IllegalStateException("이미 종료된 여정입니다.");
        }

        journey.setStatus("ABANDONED");
        journey.setCompletedAt(LocalDateTime.now());
        return toDetailDto(journey);
    }

    @Override
    @Transactional
    public void deleteJourney(String userEmail, Long journeyId) {
        User user = findUser(userEmail);
        RouteJourney journey = findOwnedJourney(journeyId, user);
        routeJourneyRepository.delete(journey);
    }

    @Override
    public List<RouteJourneySummaryDto> getHistory(String userEmail) {
        User user = findUser(userEmail);
        return routeJourneyRepository.findByUserOrderByStartedAtDesc(user).stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    private RouteJourney findOwnedJourney(Long journeyId, User user) {
        RouteJourney journey = routeJourneyRepository.findById(journeyId)
                .orElseThrow(() -> new EntityNotFoundException("여정을 찾을 수 없습니다: " + journeyId));
        if (!journey.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("본인의 여정만 조작할 수 있습니다.");
        }
        return journey;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다: " + email));
    }

    private RouteJourneyDetailDto toDetailDto(RouteJourney journey) {
        List<RouteJourneyCheckpointDto> checkpoints = journey.getCheckpoints().stream()
                .map(c -> RouteJourneyCheckpointDto.builder()
                        .sequenceOrder(c.getSequenceOrder())
                        .title(c.getTitle())
                        .lat(c.getLat())
                        .lng(c.getLng())
                        .photoUrl(c.getPhotoUrl())
                        .visited(c.isVisited())
                        .visitedAt(c.getVisitedAt())
                        .build())
                .collect(Collectors.toList());

        int total = checkpoints.size();
        int visited = (int) checkpoints.stream().filter(RouteJourneyCheckpointDto::isVisited).count();
        RouteJourneySummaryDto summary = toSummaryDto(journey, visited, total);

        return RouteJourneyDetailDto.builder()
                .id(summary.getId())
                .sourceType(summary.getSourceType())
                .sourceId(summary.getSourceId())
                .title(summary.getTitle())
                .totalDistanceKm(summary.getTotalDistanceKm())
                .status(summary.getStatus())
                .startedAt(summary.getStartedAt())
                .completedAt(summary.getCompletedAt())
                .walkedDistanceKm(summary.getWalkedDistanceKm())
                .elapsedSeconds(summary.getElapsedSeconds())
                .completionRate(summary.getCompletionRate())
                .visitedCheckpointCount(visited)
                .totalCheckpointCount(total)
                .checkpoints(checkpoints)
                .build();
    }

    private RouteJourneySummaryDto toSummaryDto(RouteJourney journey) {
        int total = journey.getCheckpoints().size();
        int visited = (int) journey.getCheckpoints().stream().filter(RouteJourneyCheckpoint::isVisited).count();
        return toSummaryDto(journey, visited, total);
    }

    private RouteJourneySummaryDto toSummaryDto(RouteJourney journey, int visited, int total) {
        return RouteJourneySummaryDto.builder()
                .id(journey.getId())
                .sourceType(journey.getSourceType())
                .sourceId(journey.getSourceId())
                .title(journey.getTitle())
                .totalDistanceKm(journey.getTotalDistanceKm())
                .status(journey.getStatus())
                .startedAt(journey.getStartedAt())
                .completedAt(journey.getCompletedAt())
                .walkedDistanceKm(Math.round(journey.getWalkedDistanceKm() * 100) / 100.0)
                .elapsedSeconds(journey.getElapsedSeconds())
                .completionRate(total == 0 ? 0 : (double) visited / total)
                .build();
    }

}
