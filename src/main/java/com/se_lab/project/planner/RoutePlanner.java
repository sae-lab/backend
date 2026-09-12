package com.se_lab.project.planner;

import com.se_lab.project.dto.BasePlaceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class RoutePlanner {

    private final TravelTimeService travelTimeService;

    public RoutePlanner(@Qualifier("haversineTravelTimeService") TravelTimeService travelTimeService) {
        this.travelTimeService = travelTimeService;
    }

    public List<BasePlaceDto> generateOptimalRoute(
            double currentX,
            double currentY,
            int availableMinutes,
            List<BasePlaceDto> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<BasePlaceDto> sortedCandidates = new ArrayList<>(candidates);
        sortedCandidates.sort(Comparator.comparingInt(
                place -> travelTimeService.calculateTravelTime(
                        currentX,
                        currentY,
                        place.getLongitude(),
                        place.getLatitude()
                )
        ));

        List<BasePlaceDto> route = new ArrayList<>();
        double currentRouteX = currentX;
        double currentRouteY = currentY;
        int remainingMinutes = availableMinutes;

        for (BasePlaceDto place : sortedCandidates) {
            int travelTime = travelTimeService.calculateTravelTime(
                    currentRouteX,
                    currentRouteY,
                    place.getLongitude(),
                    place.getLatitude()
            );
            int stayTime = place.getEstimatedStayTime();
            int totalTimeNeeded = travelTime + stayTime;

            log.info(
                    "후보 {} 이동시간={} 체류시간={} 남은시간={}",
                    place.getTitle(),
                    travelTime,
                    stayTime,
                    remainingMinutes
            );

            if (totalTimeNeeded <= remainingMinutes) {
                route.add(place);
                remainingMinutes -= totalTimeNeeded;
                currentRouteX = place.getLongitude();
                currentRouteY = place.getLatitude();
            }
        }

        return route;
    }
}
