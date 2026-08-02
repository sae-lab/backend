package com.se_lab.project.service;

import com.se_lab.project.constants.TourApiConstants;
import com.se_lab.project.constants.TourTimeConstants;
import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.planner.RoutePlanner;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RouteServiceImplTest {

    @Test
    void getOptimalRoute_filtersUnsupportedNearbyPlacesAndSetsStayTime() {
        BasePlaceDto supportedPlace = BasePlaceDto.builder()
                .title("근처 장소")
                .mapx(127.0)
                .mapy(37.0)
                .contentTypeId(TourApiConstants.CONTENT_TYPE_TOURIST_ATTRACTION)
                .cat1("A01")
                .cat2("A0202")
                .estimatedStayTime(0)
                .build();
        BasePlaceDto unsupportedPlace = BasePlaceDto.builder()
                .title("제외할 장소")
                .mapx(127.1)
                .mapy(37.1)
                .contentTypeId("99")
                .estimatedStayTime(0)
                .build();

        FakeTourApiService tourApiService = new FakeTourApiService(
                List.of(supportedPlace, unsupportedPlace),
                Collections.emptyList()
        );
        CapturingRoutePlanner routePlanner = new CapturingRoutePlanner(List.of(supportedPlace));
        RouteServiceImpl routeService = new RouteServiceImpl(tourApiService, routePlanner);

        List<BasePlaceDto> result = routeService.getOptimalRoute(127.0, 37.0, 90);

        assertSame(routePlanner.plannedRoute, result);
        assertEquals(TourTimeConstants.getStayTime("tourist_attraction"), supportedPlace.getEstimatedStayTime());
        assertEquals(0, unsupportedPlace.getEstimatedStayTime());
        assertEquals(1, routePlanner.capturedCandidates.size());
        assertSame(supportedPlace, routePlanner.capturedCandidates.get(0));
        assertEquals(1, tourApiService.nearbyCalls);
        assertEquals(0, tourApiService.areaCalls);
    }

    @Test
    void getOptimalRoute_fallsBackToAreaPlacesWhenNearbyIsEmpty() {
        BasePlaceDto fallbackSupportedPlace = BasePlaceDto.builder()
                .title("대체 장소")
                .mapx(128.0)
                .mapy(38.0)
                .contentTypeId(TourApiConstants.CONTENT_TYPE_CULTURE)
                .cat1("A01")
                .cat2("A0202")
                .estimatedStayTime(0)
                .build();
        BasePlaceDto fallbackUnsupportedPlace = BasePlaceDto.builder()
                .title("대체 제외 장소")
                .mapx(128.1)
                .mapy(38.1)
                .contentTypeId("99")
                .estimatedStayTime(0)
                .build();
        FakeTourApiService tourApiService = new FakeTourApiService(
                Collections.emptyList(),
                List.of(fallbackSupportedPlace, fallbackUnsupportedPlace)
        );
        CapturingRoutePlanner routePlanner = new CapturingRoutePlanner(Collections.emptyList());
        RouteServiceImpl routeService = new RouteServiceImpl(tourApiService, routePlanner);

        List<BasePlaceDto> result = routeService.getOptimalRoute(127.0, 37.0, 60);

        assertSame(routePlanner.plannedRoute, result);
        assertEquals(0, fallbackSupportedPlace.getEstimatedStayTime());
        assertEquals(0, fallbackUnsupportedPlace.getEstimatedStayTime());
        assertEquals(0, routePlanner.capturedCandidates.size());
        assertEquals(1, tourApiService.nearbyCalls);
        assertEquals(0, tourApiService.areaCalls);
    }

    private static class FakeTourApiService extends TourApiService {
        private final List<BasePlaceDto> nearbyPlaces;
        private final List<BasePlaceDto> areaPlaces;
        private int nearbyCalls;
        private int areaCalls;

        FakeTourApiService(List<BasePlaceDto> nearbyPlaces, List<BasePlaceDto> areaPlaces) {
            super(new RestTemplate(), new ObjectMapper(), "", "", "", "", "");
            this.nearbyPlaces = nearbyPlaces;
            this.areaPlaces = areaPlaces;
        }

        @Override
        public List<BasePlaceDto> getNearbyPlaces(String mapX, String mapY) {
            nearbyCalls++;
            return nearbyPlaces;
        }

        @Override
        public List<BasePlaceDto> getPlacesByArea(String areaCode, String sigunguCode, String contentTypeId, int numOfRows) {
            areaCalls++;
            return areaPlaces;
        }

        @Override
        public List<BasePlaceDto> searchByKeyword(String keyword, int numOfRows) {
            return Collections.emptyList();
        }
    }

    private static class CapturingRoutePlanner extends RoutePlanner {
        private final List<BasePlaceDto> plannedRoute;
        private List<BasePlaceDto> capturedCandidates = Collections.emptyList();

        CapturingRoutePlanner(List<BasePlaceDto> plannedRoute) {
            super((x1, y1, x2, y2) -> 1);
            this.plannedRoute = plannedRoute;
        }

        @Override
        public List<BasePlaceDto> generateOptimalRoute(double currentX, double currentY, int availableMinutes, List<BasePlaceDto> candidates) {
            this.capturedCandidates = new ArrayList<>(candidates);
            return plannedRoute;
        }
    }
}

