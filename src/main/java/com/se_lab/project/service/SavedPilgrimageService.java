package com.se_lab.project.service;

import com.se_lab.project.dto.PilgrimageRouteSummaryDto;

import java.util.List;

public interface SavedPilgrimageService {
    List<PilgrimageRouteSummaryDto> getSavedRoutes(String userEmail);
    boolean saveRoute(String userEmail, Long routeId);
    boolean unsaveRoute(String userEmail, Long routeId);
}
