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

    /// 지난 여정 하나를 자세히 본다. 진행 중이든 끝났든 본인 것이면 볼 수 있다.
    RouteJourneyDetailDto getJourney(String userEmail, Long journeyId);

    RouteJourneyDetailDto ping(String userEmail, Long journeyId, double lat, double lng);

    RouteJourneyDetailDto abandonJourney(String userEmail, Long journeyId);

    void deleteJourney(String userEmail, Long journeyId);

    List<RouteJourneySummaryDto> getHistory(String userEmail);
}
