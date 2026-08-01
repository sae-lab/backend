package com.se_lab.project.planner;

public interface TravelTimeService {
    // 두 좌표 간 이동 시간(분) 계산
    int calculateTravelTime(double startX, double startY, double endX, double endY);
}
