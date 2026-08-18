package com.se_lab.project.planner;

import com.se_lab.project.dto.BasePlaceDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RoutePlanner {
    private final TravelTimeService travelTimeService;

    public RoutePlanner(@Qualifier("haversineTravelTimeService") TravelTimeService travelTimeService) {
        this.travelTimeService = travelTimeService;
    }

    public List<BasePlaceDto> generateOptimalRoute(
            double currentLongitude, double currentLatitude, int availableMinutes,
            List<BasePlaceDto> candidates) {
        List<BasePlaceDto> sortedCandidates = new ArrayList<>(candidates);
        final double originLongitude = currentLongitude;
        final double originLatitude = currentLatitude;
        sortedCandidates.sort(Comparator.comparingInt(place -> travelTimeService.calculateTravelTime(
                originLongitude, originLatitude, place.getLongitude(), place.getLatitude())));

        List<BasePlaceDto> route = new ArrayList<>();
        for (BasePlaceDto place : sortedCandidates) {
            int travelMinutes = travelTimeService.calculateTravelTime(
                    currentLongitude, currentLatitude, place.getLongitude(), place.getLatitude());
            int requiredMinutes = travelMinutes + place.getEstimatedStayTime();
            if (requiredMinutes <= availableMinutes) {
                route.add(place);
                availableMinutes -= requiredMinutes;
                currentLongitude = place.getLongitude();
                currentLatitude = place.getLatitude();
            }
        }
        return route;
    }
}
