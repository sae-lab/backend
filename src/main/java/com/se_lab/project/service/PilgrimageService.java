package com.se_lab.project.service;

import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;

import java.util.List;

public interface PilgrimageService {
    List<PilgrimageRouteSummaryDto> getAllRoutes();
    PilgrimageRouteDetailDto getRouteDetail(Long id);

    /// 이름·설명·거리 같은 요약만. 상세와 달리 구간 스팟을 관광 API로 새로 찾지 않아 빠르다.
    PilgrimageRouteSummaryDto getRouteSummary(Long id);
    PilgrimageRouteSummaryDto generateRandomRoute(String category);
}
