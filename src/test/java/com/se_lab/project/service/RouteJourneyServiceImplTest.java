package com.se_lab.project.service;

import com.se_lab.project.dto.RouteJourneyCheckpointDto;
import com.se_lab.project.dto.RouteJourneyDetailDto;
import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteWaypoint;
import com.se_lab.project.repository.RouteJourneyRepository;
import com.se_lab.project.repository.UserRepository;
import com.se_lab.project.repository.UserRouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private RouteJourneyServiceImpl routeJourneyService;

    @Test
    void refusesToStartJourneyFromPostMadeOfTourSpots() {
        User walker = walkerWithoutActiveJourney();
        UserRoute route = UserRoute.builder().author(walker).title("관광지 게시물").build();
        route.addWaypoint(UserRouteWaypoint.builder()
                .sequenceOrder(1).contentId("126508").title("").lat(0).lng(0)
                .build());
        when(userRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));

        // 체크포인트가 원본 좌표를 복사하는 구조라, 시작하면 관광 데이터를 다시 저장하게 된다.
        assertThatThrownBy(() -> routeJourneyService.startJourney(WALKER_EMAIL, "USER_ROUTE", ROUTE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("여행으로 추가할 수 없습니다");
        verify(routeJourneyRepository, never()).save(any());
    }

    @Test
    void startsJourneyFromPostOfOwnWaypoints() {
        User walker = walkerWithoutActiveJourney();
        UserRoute route = UserRoute.builder().author(walker).title("내 산책길").build();
        route.addWaypoint(UserRouteWaypoint.builder()
                .sequenceOrder(1).title("출발").lat(38.20).lng(128.59).build());
        route.addWaypoint(UserRouteWaypoint.builder()
                .sequenceOrder(2).title("도착").lat(38.21).lng(128.60).build());
        when(userRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));
        when(routeJourneyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RouteJourneyDetailDto journey = routeJourneyService.startJourney(WALKER_EMAIL, "USER_ROUTE", ROUTE_ID);

        // 막는 조건이 사용자가 직접 찍은 게시물까지 막으면 안 된다.
        assertThat(journey.getTotalCheckpointCount()).isEqualTo(2);
        assertThat(journey.getCheckpoints())
                .extracting(RouteJourneyCheckpointDto::getTitle)
                .containsExactly("출발", "도착");
    }

    private User walkerWithoutActiveJourney() {
        User walker = mock(User.class);
        when(userRepository.findByEmail(WALKER_EMAIL)).thenReturn(Optional.of(walker));
        when(routeJourneyRepository.findByUserAndStatus(walker, "IN_PROGRESS")).thenReturn(Optional.empty());
        return walker;
    }
}
