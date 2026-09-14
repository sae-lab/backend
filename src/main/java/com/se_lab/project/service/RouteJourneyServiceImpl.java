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
import com.se_lab.project.service.TourSpotLookupService.TourSpot;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 여정 추적.
///
/// 스팟 도착 판정과 걸은 거리·시간 계산은 앱이 한다. 사용자 GPS 좌표는 서버로 오지 않고,
/// 앱이 "몇 번 스팟 도착"과 누적 기록만 보낸다. 개인 위치를 서버로 전송하는 것 자체가
/// 위치기반서비스사업자 신고 대상이기 때문이다 (#57).
///
/// 관광지 체크포인트는 콘텐츠 ID만 저장하고, 보여줄 때 한국관광공사 API로 이름·좌표·사진을 채운다 (#58).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteJourneyServiceImpl implements RouteJourneyService {

    // 앱이 보내는 누적 시간의 상한 여유. 기기 시계와 서버 시계가 조금 어긋나도 거부하지 않게 둔다.
    private static final long ELAPSED_CLOCK_SLACK_SECONDS = 300;

    private final RouteJourneyRepository routeJourneyRepository;
    private final UserRepository userRepository;
    private final UserRouteRepository userRouteRepository;
    private final SavedPilgrimageService savedPilgrimageService;
    private final PilgrimageService pilgrimageService;
    private final TourSpotLookupService tourSpotLookupService;

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

        // 순례길 스팟은 상세를 열 때마다 관광 API로 새로 찾아서 시점에 따라 달라질 수 있다.
        // 그래서 시작 시점의 스팟을 콘텐츠 ID로 고정한다. 같은 스팟이 두 구간에 걸쳐 나오면 한 번만 넣는다.
        Set<String> seen = new HashSet<>();
        int sequence = 1;
        for (PilgrimageSegmentDto segment : pilgrimage.getSegments()) {
            for (BasePlaceDto spot : segment.getSpots()) {
                String contentId = spot.getContentId();
                // 콘텐츠 ID가 없으면 나중에 다시 조회할 방법이 없으므로 넣지 않는다.
                if (contentId == null || contentId.isBlank() || !seen.add(contentId)) continue;
                journey.addCheckpoint(tourCheckpoint(sequence++, contentId));
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
            if (wp.isTourSpot()) {
                journey.addCheckpoint(tourCheckpoint(sequence++, wp.getContentId()));
                continue;
            }
            // 사용자가 직접 찍은 지점은 게시물 내용이라 그대로 옮긴다.
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

    /// 관광지 체크포인트. 공사 데이터(이름·좌표·사진)는 저장하지 않고 NOT NULL 칸은 빈 값으로 둔다.
    private RouteJourneyCheckpoint tourCheckpoint(int sequenceOrder, String contentId) {
        return RouteJourneyCheckpoint.builder()
                .sequenceOrder(sequenceOrder)
                .contentId(contentId)
                .title("")
                .lat(0)
                .lng(0)
                .build();
    }

    // 관광지 웨이포인트는 좌표를 저장하지 않으므로 시작 시점에 조회한 좌표로 계산한다.
    // 남는 것은 계산된 거리뿐이다.
    private Double estimatePathDistanceKm(List<UserRouteWaypoint> waypoints) {
        Map<String, TourSpot> spots = tourSpotLookupService.findAll(waypoints.stream()
                .filter(UserRouteWaypoint::isTourSpot)
                .map(UserRouteWaypoint::getContentId)
                .toList());

        List<double[]> points = new ArrayList<>();
        for (UserRouteWaypoint wp : waypoints) {
            if (!wp.isTourSpot()) {
                points.add(new double[]{wp.getLat(), wp.getLng()});
                continue;
            }
            TourSpot spot = spots.get(wp.getContentId());
            if (spot != null) points.add(new double[]{spot.lat(), spot.lng()});
        }

        if (points.size() < 2) return null;
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            double[] prev = points.get(i - 1);
            double[] cur = points.get(i);
            total += GeoUtils.distanceKm(prev[0], prev[1], cur[0], cur[1]);
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
    public RouteJourneyDetailDto markCheckpointVisited(String userEmail, Long journeyId, int sequenceOrder) {
        User user = findUser(userEmail);
        RouteJourney journey = findOwnedJourney(journeyId, user);
        requireInProgress(journey);

        RouteJourneyCheckpoint checkpoint = journey.getCheckpoints().stream()
                .filter(c -> c.getSequenceOrder() == sequenceOrder)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("체크포인트를 찾을 수 없습니다: " + sequenceOrder));

        LocalDateTime now = LocalDateTime.now();
        // 네트워크 재시도로 같은 도착이 두 번 와도 처음 찍은 시각을 지킨다.
        if (!checkpoint.isVisited()) {
            checkpoint.setVisited(true);
            checkpoint.setVisitedAt(now);
        }

        Map<String, TourSpot> spots = resolveTourSpots(journey);
        // 완주는 화면에 보이는 스팟 기준으로 판정한다.
        // 공사 데이터에서 없어진 스팟 때문에 영영 완주하지 못하면 안 된다.
        List<RouteJourneyCheckpointDto> visible = toCheckpointDtos(journey, spots);
        boolean allVisited = !visible.isEmpty() && visible.stream().allMatch(RouteJourneyCheckpointDto::isVisited);
        if (allVisited) {
            journey.setStatus("COMPLETED");
            journey.setCompletedAt(now);
        }

        return toDetailDto(journey, spots);
    }

    @Override
    @Transactional
    public void saveProgress(String userEmail, Long journeyId, double walkedDistanceKm, long elapsedSeconds) {
        User user = findUser(userEmail);
        RouteJourney journey = findOwnedJourney(journeyId, user);
        requireInProgress(journey);

        if (!Double.isFinite(walkedDistanceKm) || walkedDistanceKm < 0 || elapsedSeconds < 0) {
            throw new IllegalArgumentException("기록 값이 올바르지 않습니다.");
        }

        // 여정을 시작한 뒤 흐른 시간보다 오래 걸었을 수는 없다.
        long maxElapsed = Duration.between(journey.getStartedAt(), LocalDateTime.now()).getSeconds()
                + ELAPSED_CLOCK_SLACK_SECONDS;

        // 앱은 누적값을 보낸다. 늦게 도착한 이전 요청이 기록을 되돌리지 않도록 줄어드는 값은 무시한다.
        journey.setWalkedDistanceKm(Math.max(journey.getWalkedDistanceKm(), walkedDistanceKm));
        journey.setElapsedSeconds(Math.max(journey.getElapsedSeconds(), Math.min(elapsedSeconds, maxElapsed)));
    }

    @Override
    public RouteJourneyDetailDto getJourney(String userEmail, Long journeyId) {
        User user = findUser(userEmail);
        // 남의 여정은 볼 수 없다. findOwnedJourney가 소유자 검사를 한다.
        return toDetailDto(findOwnedJourney(journeyId, user));
    }

    @Override
    @Transactional
    public RouteJourneyDetailDto abandonJourney(String userEmail, Long journeyId) {
        User user = findUser(userEmail);
        RouteJourney journey = findOwnedJourney(journeyId, user);
        requireInProgress(journey);

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
                .toList();
    }

    private void requireInProgress(RouteJourney journey) {
        if (!"IN_PROGRESS".equals(journey.getStatus())) {
            throw new IllegalStateException("이미 종료된 여정입니다.");
        }
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

    private Map<String, TourSpot> resolveTourSpots(RouteJourney journey) {
        return tourSpotLookupService.findAll(journey.getCheckpoints().stream()
                .filter(RouteJourneyCheckpoint::isTourSpot)
                .map(RouteJourneyCheckpoint::getContentId)
                .toList());
    }

    private RouteJourneyDetailDto toDetailDto(RouteJourney journey) {
        return toDetailDto(journey, resolveTourSpots(journey));
    }

    private RouteJourneyDetailDto toDetailDto(RouteJourney journey, Map<String, TourSpot> spots) {
        List<RouteJourneyCheckpointDto> checkpoints = toCheckpointDtos(journey, spots);

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

    // 관광지 체크포인트는 실시간 조회 값으로 채운다. 조회에 실패한 스팟은 뺀다 —
    // 좌표 0,0으로 넘기면 지도 범위와 앱의 도착 판정이 깨진다.
    private List<RouteJourneyCheckpointDto> toCheckpointDtos(RouteJourney journey, Map<String, TourSpot> spots) {
        List<RouteJourneyCheckpointDto> checkpoints = new ArrayList<>();
        for (RouteJourneyCheckpoint c : journey.getCheckpoints()) {
            if (!c.isTourSpot()) {
                checkpoints.add(toCheckpointDto(c, c.getTitle(), c.getLat(), c.getLng(), c.getPhotoUrl()));
                continue;
            }
            TourSpot spot = spots.get(c.getContentId());
            if (spot != null) {
                checkpoints.add(toCheckpointDto(c, spot.title(), spot.lat(), spot.lng(), spot.photoUrl()));
            }
        }
        return checkpoints;
    }

    private RouteJourneyCheckpointDto toCheckpointDto(RouteJourneyCheckpoint c, String title,
                                                      double lat, double lng, String photoUrl) {
        return RouteJourneyCheckpointDto.builder()
                .sequenceOrder(c.getSequenceOrder())
                .title(title)
                .lat(lat)
                .lng(lng)
                .photoUrl(photoUrl)
                .visited(c.isVisited())
                .visitedAt(c.getVisitedAt())
                .build();
    }

    // 지난 여정 목록용. 여정마다 관광 API를 부르지 않도록 저장된 체크포인트 수로 계산한다.
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
