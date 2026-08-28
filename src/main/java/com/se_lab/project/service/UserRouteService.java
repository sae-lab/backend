package com.se_lab.project.service;

import com.se_lab.project.dto.Coordinate;
import com.se_lab.project.dto.UserRouteCommentDto;
import com.se_lab.project.dto.UserRouteDetailDto;
import com.se_lab.project.dto.UserRouteSummaryDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserRouteService {
    List<UserRouteSummaryDto> getAllRoutes(String currentUserEmail, String routeType, String sort);

    List<UserRouteSummaryDto> getMyRoutes(String authorEmail, String routeType);

    List<UserRouteSummaryDto> getMyScraps(String userEmail, String routeType);

    UserRouteDetailDto getRouteDetail(Long id, String currentUserEmail);

    // 1번 웨이포인트(시작 위치)에서 가장 가까운 순서대로 재정렬한 뒤, 그 순서를 따라
    // 실제 걸을 수 있는 도로 기준 경로 좌표를 이어 붙여 반환한다.
    List<Coordinate> getWalkingPath(Long routeId);

    Long createRoute(String authorEmail, String title, String description, String routeType);

    // 저장해둔 AI 순례길(PilgrimageRoute)의 구간별 스팟들을 그대로 웨이포인트로 옮겨
    // 게시판 게시물로 변환한다. 사진은 사용자가 직접 찍은 게 아니라 관광 API 썸네일이다.
    Long createFromPilgrimage(Long pilgrimageRouteId, String authorEmail, String routeType);

    void deleteRoute(Long routeId, String requesterEmail);

    void addWaypoint(Long routeId, String authorEmail, String title, String memo,
                      double lat, double lng, MultipartFile photo);

    boolean toggleLike(Long routeId, String userEmail);

    boolean toggleScrap(Long routeId, String userEmail);

    UserRouteCommentDto addComment(Long routeId, String authorEmail, String content, Long parentId);

    void deleteComment(Long commentId, String authorEmail);
}
