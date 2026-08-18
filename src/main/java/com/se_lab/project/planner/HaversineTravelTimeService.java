package com.se_lab.project.planner;

import org.springframework.stereotype.Service;

@Service("haversineTravelTimeService")
public class HaversineTravelTimeService implements TravelTimeService {
    private static final double WALKING_SPEED_KMH = 4.0;
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public int calculateTravelTime(double startLongitude, double startLatitude,
                                   double endLongitude, double endLatitude) {
        double latitudeDifference = Math.toRadians(endLatitude - startLatitude);
        double longitudeDifference = Math.toRadians(endLongitude - startLongitude);
        double haversine = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(Math.toRadians(startLatitude)) * Math.cos(Math.toRadians(endLatitude))
                * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);
        double distanceKm = EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return Math.max(1, (int) Math.ceil(distanceKm / WALKING_SPEED_KMH * 60));
    }
}
