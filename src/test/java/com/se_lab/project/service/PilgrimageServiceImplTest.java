package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.Coordinate;
import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageSegmentDto;
import com.se_lab.project.entity.PilgrimageRoute;
import com.se_lab.project.entity.PilgrimageSegment;
import com.se_lab.project.repository.PilgrimageRouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PilgrimageServiceImplTest {

    private static final long TOUR_API_DELAY_MS = 300;

    @Mock
    private PilgrimageRouteRepository pilgrimageRouteRepository;
    @Mock
    private TourApiService tourApiService;
    @Mock
    private KakaoDirectionsService kakaoDirectionsService;
    @Mock
    private OsrmWalkingDirectionsService osrmWalkingDirectionsService;

    @InjectMocks
    private PilgrimageServiceImpl pilgrimageService;

    @Test
    void detailCallsExternalApisConcurrentlyAndKeepsOrder() {
        PilgrimageRoute route = PilgrimageRoute.builder().identifier("test").name("속초-강릉").description("설명").build();
        route.addSegment(segment(1, "속초", 38.20, 128.59, "양양", 38.07, 128.62));
        route.addSegment(segment(2, "양양", 38.07, 128.62, "강릉", 37.75, 128.88));
        when(pilgrimageRouteRepository.findById(1L)).thenReturn(Optional.of(route));

        // 도보 경로는 출발·도착 두 점만 돌려준다. 그래서 구간마다 검색 지점은 출발·도착 두 곳이다.
        when(osrmWalkingDirectionsService.getWalkingPath(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> List.of(
                        coordinate(invocation.getArgument(0), invocation.getArgument(1)),
                        coordinate(invocation.getArgument(2), invocation.getArgument(3))));
        // 관광 API는 느리고, 검색한 바로 그 자리의 스팟 하나를 돌려준다.
        when(tourApiService.getNearbyPlaces(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Thread.sleep(TOUR_API_DELAY_MS);
            double lng = Double.parseDouble(invocation.getArgument(0));
            double lat = Double.parseDouble(invocation.getArgument(1));
            return List.of(BasePlaceDto.builder()
                    .title("스팟 " + lat + "," + lng)
                    .latitude(lat).longitude(lng)
                    .contentId(lat + ":" + lng)
                    .build());
        });

        long started = System.nanoTime();
        PilgrimageRouteDetailDto detail = pilgrimageService.getRouteDetail(1L);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        // 동시에 불러도 구간 순서와 구간 안의 스팟 순서(출발 → 도착)는 그대로다.
        assertThat(detail.getSegments())
                .extracting(PilgrimageSegmentDto::getFromCity)
                .containsExactly("속초", "양양");
        assertThat(detail.getSegments().get(0).getSpots())
                .extracting(BasePlaceDto::getLatitude)
                .containsExactly(38.20, 38.07);
        assertThat(detail.getSegments().get(1).getSpots())
                .extracting(BasePlaceDto::getLatitude)
                .containsExactly(38.07, 37.75);

        // 차례로 부르면 관광 API만 구간 2개 × 검색 지점 2곳 × 300ms = 1.2초다.
        long sequentialMs = 2 * 2 * TOUR_API_DELAY_MS;
        assertThat(elapsedMs).isLessThan(sequentialMs * 2 / 3);
    }

    private static PilgrimageSegment segment(int order, String fromCity, double fromLat, double fromLng,
                                             String toCity, double toLat, double toLng) {
        return PilgrimageSegment.builder()
                .sequenceOrder(order)
                .fromCity(fromCity).fromLat(fromLat).fromLng(fromLng)
                .toCity(toCity).toLat(toLat).toLng(toLng)
                .distanceKm(15)
                .difficulty("보통")
                .estimatedMinutes(225)
                .build();
    }

    private static Coordinate coordinate(double lat, double lng) {
        return Coordinate.builder().lat(lat).lng(lng).build();
    }
}
