package com.se_lab.project.service;

import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;

import java.util.List;

public interface PilgrimageService {
    List<PilgrimageRouteSummaryDto> getAllRoutes();
    PilgrimageRouteDetailDto getRouteDetail(Long id);
    PilgrimageRouteSummaryDto generateRandomRoute(String category);
}
