package com.se_lab.project.planner;

import com.se_lab.project.dto.BasePlaceDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutePlannerTest {

    @Test
    void testGenerateOptimalRoute() {
        TravelTimeService travelTimeService = new HaversineTravelTimeService();
        RoutePlanner planner = new RoutePlanner(travelTimeService);

        BasePlaceDto p1 = BasePlaceDto.builder().contentId("1").mapx(127.0).mapy(37.0).estimatedStayTime(20).build();
        BasePlaceDto p2 = BasePlaceDto.builder().contentId("2").mapx(127.02).mapy(37.0).estimatedStayTime(20).build();

        List<BasePlaceDto> candidates = new ArrayList<>(List.of(p1, p2));

        List<BasePlaceDto> route = planner.generateOptimalRoute(127.0, 37.0, 45, candidates);

        assertEquals(1, route.size(), "45분 가용 시 첫 산책 장소만 방문 가능해야 한다");
        assertEquals("1", route.get(0).getContentId());
    }
}