package com.se_lab.project.planner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("haversineTravelTimeService")
public class HaversineTravelTimeService implements TravelTimeService {

    private static final double WALKING_SPEED_KMH = 4.0; // 도보 시속 4km

    @Override
    public int calculateTravelTime(double x1, double y1, double x2, double y2) {
        double distance = calculateDistance(y1, x1, y2, x2);

        log.info(
                "거리 계산: ({},{}) -> ({},{}) = {}km",
                x1,y1,x2,y2,distance
        );

        return Math.max(
                1,
                (int)Math.ceil(
                        (distance / WALKING_SPEED_KMH) * 60
                )
        );
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구 반지름(km)
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
