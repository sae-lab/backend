package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
import com.se_lab.project.dto.PilgrimageSegmentDto;
import com.se_lab.project.dto.RouteJourneyCheckpointDto;
import com.se_lab.project.dto.RouteJourneyDetailDto;
import com.se_lab.project.entity.RouteJourney;
import com.se_lab.project.entity.RouteJourneyCheckpoint;
import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteWaypoint;
import com.se_lab.project.repository.RouteJourneyRepository;
import com.se_lab.project.repository.UserRepository;
import com.se_lab.project.repository.UserRouteRepository;
import com.se_lab.project.service.TourSpotLookupService.TourSpot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteJourneyServiceImplTest {

    private static final String WALKER_EMAIL = "walker@example.com";
    private static final long ROUTE_ID = 5L;
    private static final long PILGRIMAGE_ID = 14L;
    private static final long JOURNEY_ID = 3L;

    @Mock
    private RouteJourneyRepository routeJourneyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRouteRepository userRouteRepository;
    @Mock
    private SavedPilgrimageService savedPilgrimageService;
    @Mock
    private PilgrimageService pilgrimageService;
    @Mock
    private TourSpotLookupService tourSpotLookupService;

    @InjectMocks
    private RouteJourneyServiceImpl routeJourneyService;

    @Test
    void startingAiPilgrimageStoresOnlyContentIds() {
        walkerWithoutActiveJourney();
        PilgrimageRouteDetailDto pilgrimage = PilgrimageRouteDetailDto.builder()
                .name("속초 순례길")
                .totalDistanceKm(12.5)
                .segments(List.of(
                        PilgrimageSegmentDto.builder().spots(List.of(place("111"), place(""))).build(),
                        // 같은 스팟이 다음 구간에도 나오면 한 번만 넣는다.
                        PilgrimageSegmentDto.builder().spots(List.of(place("111"), place("222"))).build()))
                .build();
        when(pilgrimageService.getRouteDetail(PILGRIMAGE_ID)).thenReturn(pilgrimage);
        when(routeJourneyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tourSpotLookupService.findAll(any())).thenReturn(Map.of(
                "111", spot("111", "영랑호", 38.21, 128.58),
                "222", spot("222", "청초호", 38.19, 128.59)));

        RouteJourneyDetailDto detail = routeJourneyService.startJourney(WALKER_EMAIL, "AI_PILGRIMAGE", PILGRIMAGE_ID, null);

        ArgumentCaptor<RouteJourney> saved = ArgumentCaptor.forClass(RouteJourney.class);
        verify(routeJourneyRepository).save(saved.capture());
        assertThat(saved.getValue().getCheckpoints())
                .extracting(RouteJourneyCheckpoint::getContentId)
                .containsExactly("111", "222");
        // 관광 데이터(이름·좌표·사진)는 저장하지 않는다.
        assertThat(saved.getValue().getCheckpoints()).allSatisfy(checkpoint -> {
            assertThat(checkpoint.getTitle()).isEmpty();
            assertThat(checkpoint.getLat()).isZero();
            assertThat(checkpoint.getLng()).isZero();
            assertThat(checkpoint.getPhotoUrl()).isNull();
        });
        // 화면에는 실시간 조회 값이 나간다.
        assertThat(detail.getCheckpoints())
                .extracting(RouteJourneyCheckpointDto::getTitle)
                .containsExactly("영랑호", "청초호");
    }

    @Test
    void startingAiPilgrimageWithSelectedSpotsKeepsOnlyThoseInGivenOrder() {
        walkerWithoutActiveJourney();
        when(pilgrimageService.getRouteSummary(PILGRIMAGE_ID)).thenReturn(summary());
        when(routeJourneyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        routeJourneyService.startJourney(WALKER_EMAIL, "AI_PILGRIMAGE", PILGRIMAGE_ID,
                List.of("222", " 111 ", "222", ""));

        ArgumentCaptor<RouteJourney> saved = ArgumentCaptor.forClass(RouteJourney.class);
        verify(routeJourneyRepository).save(saved.capture());
        // 화면에서 고른 순서를 따르고, 빈 값과 중복은 뺀다.
        assertThat(saved.getValue().getCheckpoints())
                .extracting(RouteJourneyCheckpoint::getContentId)
                .containsExactly("222", "111");
        assertThat(saved.getValue().getTitle()).isEqualTo("속초 순례길");
        // 고른 스팟으로 시작할 때는 스팟을 관광 API로 새로 찾는 느린 상세 조회를 하지 않는다.
        verify(pilgrimageService, never()).getRouteDetail(any());
    }

    @Test
    void startingWithEmptySelectionIsRejected() {
        walkerWithoutActiveJourney();
        when(pilgrimageService.getRouteSummary(PILGRIMAGE_ID)).thenReturn(summary());

        assertThatThrownBy(() -> routeJourneyService.startJourney(WALKER_EMAIL, "AI_PILGRIMAGE", PILGRIMAGE_ID, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하나 이상");
        verify(routeJourneyRepository, never()).save(any());
    }

    @Test
    void startingPostCopiesOwnWaypointsAndKeepsOnlyIdForTourSpots() {
        User walker = walkerWithoutActiveJourney();
        UserRoute route = UserRoute.builder().author(walker).title("속초 산책").build();
        route.addWaypoint(UserRouteWaypoint.builder()
                .sequenceOrder(1).title("출발").lat(38.20).lng(128.59).photoUrl("/uploads/me.jpg").build());
        route.addWaypoint(UserRouteWaypoint.builder()
                .sequenceOrder(2).contentId("126508").title("").lat(0).lng(0).build());
        when(userRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));
        when(routeJourneyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tourSpotLookupService.findAll(any())).thenReturn(Map.of("126508", spot("126508", "영랑호", 38.21, 128.58)));

        RouteJourneyDetailDto detail = routeJourneyService.startJourney(WALKER_EMAIL, "USER_ROUTE", ROUTE_ID, null);

        ArgumentCaptor<RouteJourney> saved = ArgumentCaptor.forClass(RouteJourney.class);
        verify(routeJourneyRepository).save(saved.capture());
        List<RouteJourneyCheckpoint> checkpoints = saved.getValue().getCheckpoints();
        // 사용자가 직접 찍은 지점은 게시물 내용이라 그대로 옮긴다.
        assertThat(checkpoints.get(0).getTitle()).isEqualTo("출발");
        assertThat(checkpoints.get(0).getContentId()).isNull();
        assertThat(checkpoints.get(1).getContentId()).isEqualTo("126508");
        assertThat(checkpoints.get(1).getTitle()).isEmpty();

        assertThat(detail.getCheckpoints())
                .extracting(RouteJourneyCheckpointDto::getTitle)
                .containsExactly("출발", "영랑호");
        // 거리는 시작 시점에 조회한 좌표로 계산한다. 0,0 자리값으로 계산하면 수천 km가 나온다.
        assertThat(saved.getValue().getTotalDistanceKm()).isBetween(0.5, 3.0);
    }

    @Test
    void detailDropsTourSpotsThatNoLongerResolve() {
        User walker = walker(7L);
        journeyOf(walker, tourCheckpoint(1, "111"), tourCheckpoint(2, "999"));
        when(tourSpotLookupService.findAll(any())).thenReturn(Map.of("111", spot("111", "영랑호", 38.21, 128.58)));

        RouteJourneyDetailDto detail = routeJourneyService.getJourney(WALKER_EMAIL, JOURNEY_ID);

        // 좌표 0,0으로 넘기면 지도와 앱의 도착 판정이 깨진다.
        assertThat(detail.getCheckpoints())
                .extracting(RouteJourneyCheckpointDto::getSequenceOrder)
                .containsExactly(1);
        assertThat(detail.getTotalCheckpointCount()).isEqualTo(1);
    }

    @Test
    void visitingLastVisibleCheckpointCompletesJourney() {
        User walker = walker(7L);
        RouteJourney journey = journeyOf(walker, tourCheckpoint(1, "111"), tourCheckpoint(2, "999"));
        when(tourSpotLookupService.findAll(any())).thenReturn(Map.of("111", spot("111", "영랑호", 38.21, 128.58)));

        RouteJourneyDetailDto detail = routeJourneyService.markCheckpointVisited(WALKER_EMAIL, JOURNEY_ID, 1);

        // 2번 스팟은 공사 데이터에서 없어져 화면에 없다. 그 때문에 영영 완주를 못 하면 안 된다.
        assertThat(detail.getStatus()).isEqualTo("COMPLETED");
        assertThat(journey.getCompletedAt()).isNotNull();
        assertThat(journey.getCheckpoints().get(0).getVisitedAt()).isNotNull();
    }

    @Test
    void visitingAgainKeepsFirstStampTime() {
        User walker = walker(7L);
        LocalDateTime firstStamp = LocalDateTime.of(2026, 9, 14, 10, 0);
        RouteJourneyCheckpoint stamped = ownCheckpoint(1);
        stamped.setVisited(true);
        stamped.setVisitedAt(firstStamp);
        RouteJourney journey = journeyOf(walker, stamped, ownCheckpoint(2));

        RouteJourneyDetailDto detail = routeJourneyService.markCheckpointVisited(WALKER_EMAIL, JOURNEY_ID, 1);

        // 네트워크 재시도로 같은 도착이 다시 와도 처음 시각을 지킨다.
        assertThat(journey.getCheckpoints().get(0).getVisitedAt()).isEqualTo(firstStamp);
        assertThat(detail.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void progressNeverGoesBackwardsAndCannotExceedTimeSinceStart() {
        User walker = walker(7L);
        RouteJourney journey = journeyOf(walker, ownCheckpoint(1));
        journey.setWalkedDistanceKm(1.5);
        journey.setElapsedSeconds(300);

        // 10분 전에 시작한 여정인데 몇 시간을 걸었다고 오면 받아들이지 않는다.
        routeJourneyService.saveProgress(WALKER_EMAIL, JOURNEY_ID, 1.2, 99_999);

        assertThat(journey.getWalkedDistanceKm()).isEqualTo(1.5);
        assertThat(journey.getElapsedSeconds()).isBetween(880L, 920L);

        // 늦게 도착한 예전 요청이 기록을 되돌리면 안 된다.
        routeJourneyService.saveProgress(WALKER_EMAIL, JOURNEY_ID, 2.0, 400);

        assertThat(journey.getWalkedDistanceKm()).isEqualTo(2.0);
        assertThat(journey.getElapsedSeconds()).isBetween(880L, 920L);
    }

    @Test
    void progressRejectsNegativeValues() {
        User walker = walker(7L);
        journeyOf(walker, ownCheckpoint(1));

        assertThatThrownBy(() -> routeJourneyService.saveProgress(WALKER_EMAIL, JOURNEY_ID, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void progressRejectedAfterJourneyEnded() {
        User walker = walker(7L);
        RouteJourney journey = journeyOf(walker, ownCheckpoint(1));
        journey.setStatus("COMPLETED");

        assertThatThrownBy(() -> routeJourneyService.saveProgress(WALKER_EMAIL, JOURNEY_ID, 1, 10))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotStampSomeoneElsesJourney() {
        walker(7L);
        User other = mock(User.class);
        when(other.getId()).thenReturn(8L);
        journeyOf(other, ownCheckpoint(1));

        assertThatThrownBy(() -> routeJourneyService.markCheckpointVisited(WALKER_EMAIL, JOURNEY_ID, 1))
                .isInstanceOf(AccessDeniedException.class);
    }

    private User walkerWithoutActiveJourney() {
        User walker = mock(User.class);
        when(userRepository.findByEmail(WALKER_EMAIL)).thenReturn(Optional.of(walker));
        when(routeJourneyRepository.findByUserAndStatus(walker, "IN_PROGRESS")).thenReturn(Optional.empty());
        return walker;
    }

    private User walker(long id) {
        User walker = mock(User.class);
        when(walker.getId()).thenReturn(id);
        when(userRepository.findByEmail(WALKER_EMAIL)).thenReturn(Optional.of(walker));
        return walker;
    }

    private RouteJourney journeyOf(User owner, RouteJourneyCheckpoint... checkpoints) {
        RouteJourney journey = RouteJourney.builder()
                .user(owner)
                .sourceType("AI_PILGRIMAGE")
                .sourceId(PILGRIMAGE_ID)
                .title("속초 순례길")
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        for (RouteJourneyCheckpoint checkpoint : checkpoints) {
            journey.addCheckpoint(checkpoint);
        }
        when(routeJourneyRepository.findById(JOURNEY_ID)).thenReturn(Optional.of(journey));
        return journey;
    }

    private static PilgrimageRouteSummaryDto summary() {
        return PilgrimageRouteSummaryDto.builder()
                .id(PILGRIMAGE_ID).name("속초 순례길").description("설명").totalDistanceKm(12.5)
                .build();
    }

    private static RouteJourneyCheckpoint tourCheckpoint(int sequenceOrder, String contentId) {
        return RouteJourneyCheckpoint.builder()
                .sequenceOrder(sequenceOrder).contentId(contentId).title("").lat(0).lng(0)
                .build();
    }

    private static RouteJourneyCheckpoint ownCheckpoint(int sequenceOrder) {
        return RouteJourneyCheckpoint.builder()
                .sequenceOrder(sequenceOrder).title("지점 " + sequenceOrder).lat(38.2).lng(128.59)
                .build();
    }

    private static BasePlaceDto place(String contentId) {
        return BasePlaceDto.builder().title("저장하면 안 되는 이름").latitude(38.2).longitude(128.5)
                .thumbnailUrl("https://tong.visitkorea.or.kr/a.jpg").contentId(contentId)
                .build();
    }

    private static TourSpot spot(String contentId, String title, double lat, double lng) {
        return new TourSpot(contentId, title, "강원특별자치도 속초시", lat, lng, "https://tong.visitkorea.or.kr/a.jpg");
    }
}
