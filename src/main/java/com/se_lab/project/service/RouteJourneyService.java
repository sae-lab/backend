package com.se_lab.project.service;

import com.se_lab.project.dto.RouteJourneyDetailDto;
import com.se_lab.project.dto.RouteJourneySummaryDto;
import com.se_lab.project.dto.TrackableRouteDto;

import java.util.List;

public interface RouteJourneyService {
    // 추적을 시작할 수 있는 후보 경로들 (저장한 AI 순례길 + 내가 올렸거나 스크랩한 게시물)
    List<TrackableRouteDto> getTrackableRoutes(String userEmail);

    RouteJourneyDetailDto startJourney(String userEmail, String sourceType, Long sourceId);

    // 진행 중인 여정이 없으면 null
    RouteJourneyDetailDto getActiveJourney(String userEmail);

    RouteJourneyDetailDto ping(String userEmail, Long journeyId, double lat, double lng);

    RouteJourneyDetailDto abandonJourney(String userEmail, Long journeyId);

    void deleteJourney(String userEmail, Long journeyId);

    List<RouteJourneySummaryDto> getHistory(String userEmail);
}
