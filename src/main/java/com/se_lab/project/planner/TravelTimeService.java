package com.se_lab.project.planner;

public interface TravelTimeService {
    int calculateTravelTime(double startLongitude, double startLatitude,
                            double endLongitude, double endLatitude);
}
