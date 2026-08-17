package com.se_lab.project.service;

import com.se_lab.project.dto.UserRouteCommentDto;
import com.se_lab.project.dto.UserRouteDetailDto;
import com.se_lab.project.dto.UserRouteSummaryDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserRouteService {
    List<UserRouteSummaryDto> getAllRoutes(String currentUserEmail);

    UserRouteDetailDto getRouteDetail(Long id, String currentUserEmail);

    Long createRoute(String authorEmail, String title, String description);

    void addWaypoint(Long routeId, String authorEmail, String title, String memo,
                      double lat, double lng, MultipartFile photo);

    boolean toggleLike(Long routeId, String userEmail);

    UserRouteCommentDto addComment(Long routeId, String authorEmail, String content);

    void deleteComment(Long commentId, String authorEmail);
}
