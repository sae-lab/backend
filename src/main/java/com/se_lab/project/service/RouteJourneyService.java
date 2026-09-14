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

    /// 앱이 판정한 스팟 도착을 기록한다. 보이는 스팟을 모두 찍으면 완주 처리한다.
    RouteJourneyDetailDto markCheckpointVisited(String userEmail, Long journeyId, int sequenceOrder);

    /// 앱이 계산한 누적 걸은 거리·시간을 저장한다. 사용자 좌표는 받지 않는다.
    void saveProgress(String userEmail, Long journeyId, double walkedDistanceKm, long elapsedSeconds);

    RouteJourneyDetailDto abandonJourney(String userEmail, Long journeyId);

    void deleteJourney(String userEmail, Long journeyId);

    List<RouteJourneySummaryDto> getHistory(String userEmail);
}
