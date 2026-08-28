package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.Coordinate;
import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageSegmentDto;
import com.se_lab.project.dto.UserRouteCommentDto;
import com.se_lab.project.dto.UserRouteDetailDto;
import com.se_lab.project.dto.UserRouteSummaryDto;
import com.se_lab.project.dto.UserRouteWaypointDto;
import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteComment;
import com.se_lab.project.entity.UserRouteLike;
import com.se_lab.project.entity.UserRouteScrap;
import com.se_lab.project.entity.UserRouteWaypoint;
import com.se_lab.project.repository.UserRouteCommentRepository;
import com.se_lab.project.repository.UserRouteLikeRepository;
import com.se_lab.project.repository.UserRouteRepository;
import com.se_lab.project.repository.UserRouteScrapRepository;
import com.se_lab.project.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRouteServiceImpl implements UserRouteService {

    private final UserRouteRepository userRouteRepository;
    private final UserRouteLikeRepository userRouteLikeRepository;
    private final UserRouteScrapRepository userRouteScrapRepository;
    private final UserRouteCommentRepository userRouteCommentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PilgrimageService pilgrimageService;
    private final OsrmWalkingDirectionsService osrmWalkingDirectionsService;

    // 이보다 웨이포인트가 많으면(예: AI 순례길에서 옮겨진 대형 게시물) 다리(leg)마다
    // 외부 도보 경로 API를 부르는 비용이 너무 커져서, 재정렬만 하고 직선으로 잇는다.
    private static final int MAX_WAYPOINTS_FOR_REAL_ROUTING = 20;

    @Override
    public List<UserRouteSummaryDto> getAllRoutes(String currentUserEmail, String routeType, String sort) {
        User currentUser = findUserOrNull(currentUserEmail);
        List<UserRoute> routes = routeType == null
                ? userRouteRepository.findAllByOrderByCreatedAtDesc()
                : userRouteRepository.findAllByRouteTypeOrderByCreatedAtDesc(routeType);
        List<UserRouteSummaryDto> dtos = routes.stream()
                .map(route -> toSummaryDto(route, currentUser))
                .collect(Collectors.toList());
        return sortSummaries(dtos, sort);
    }

    @Override
    public List<UserRouteSummaryDto> getMyRoutes(String authorEmail, String routeType) {
        User author = findUser(authorEmail);
        List<UserRoute> routes = routeType == null
                ? userRouteRepository.findByAuthorOrderByCreatedAtDesc(author)
                : userRouteRepository.findByAuthorAndRouteTypeOrderByCreatedAtDesc(author, routeType);
        return routes.stream()
                .map(route -> toSummaryDto(route, author))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserRouteSummaryDto> getMyScraps(String userEmail, String routeType) {
        User user = findUser(userEmail);
        return userRouteScrapRepository.findByUserOrderByScrapedAtDesc(user).stream()
                .map(UserRouteScrap::getRoute)
                .filter(route -> routeType == null || routeType.equals(route.getRouteType()))
                .map(route -> toSummaryDto(route, user))
                .collect(Collectors.toList());
    }

    private List<UserRouteSummaryDto> sortSummaries(List<UserRouteSummaryDto> dtos, String sort) {
        if ("likes".equals(sort)) {
            dtos.sort(Comparator.comparingLong(UserRouteSummaryDto::getLikeCount).reversed());
        } else if ("scraps".equals(sort)) {
            dtos.sort(Comparator.comparingLong(UserRouteSummaryDto::getScrapCount).reversed());
        }
        return dtos;
    }

    @Override
    public UserRouteDetailDto getRouteDetail(Long id, String currentUserEmail) {
        UserRoute route = userRouteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + id));
        User currentUser = findUserOrNull(currentUserEmail);

        List<UserRouteWaypointDto> waypointDtos = route.getWaypoints().stream()
                .map(this::toWaypointDto)
                .collect(Collectors.toList());

        List<UserRouteCommentDto> commentDtos = userRouteCommentRepository.findByRouteAndParentIsNullOrderByCreatedAtAsc(route).stream()
                .map(comment -> toCommentDtoWithReplies(comment, currentUser))
                .collect(Collectors.toList());

        long likeCount = userRouteLikeRepository.countByRoute(route);
        boolean likedByMe = currentUser != null && userRouteLikeRepository.existsByUserAndRoute(currentUser, route);
        long scrapCount = userRouteScrapRepository.countByRoute(route);
        boolean scrapedByMe = currentUser != null && userRouteScrapRepository.existsByUserAndRoute(currentUser, route);
        boolean mine = currentUser != null && route.getAuthor().getId().equals(currentUser.getId());

        return UserRouteDetailDto.builder()
                .id(route.getId())
                .title(route.getTitle())
                .description(route.getDescription())
                .routeType(route.getRouteType())
                .authorName(route.getAuthor().getDisplayName())
                .authorProfileImageUrl(route.getAuthor().getProfileImageUrl())
                .mine(mine)
                .createdAt(route.getCreatedAt())
                .waypoints(waypointDtos)
                .likeCount(likeCount)
                .likedByMe(likedByMe)
                .scrapCount(scrapCount)
                .scrapedByMe(scrapedByMe)
                .comments(commentDtos)
                .build();
    }

    @Override
    @Transactional
    public Long createRoute(String authorEmail, String title, String description, String routeType) {
        User author = findUser(authorEmail);
        UserRoute route = UserRoute.builder()
                .author(author)
                .title(title)
                .description(description)
                .routeType(routeType != null ? routeType : "WALK")
                .build();
        return userRouteRepository.save(route).getId();
    }

    @Override
    @Transactional
    public Long createFromPilgrimage(Long pilgrimageRouteId, String authorEmail, String routeType) {
        User author = findUser(authorEmail);
        // 구간별 스팟은 PilgrimageRoute 엔티티 자체엔 저장돼 있지 않고 상세 조회 시 매번
        // 관광 API로 다시 찾아오므로, 그 로직을 그대로 재사용해서 실제 웨이포인트 후보를 얻는다.
        PilgrimageRouteDetailDto pilgrimage = pilgrimageService.getRouteDetail(pilgrimageRouteId);

        UserRoute route = UserRoute.builder()
                .author(author)
                .title(pilgrimage.getName())
                .description(pilgrimage.getDescription())
                .routeType(routeType != null ? routeType : "PILGRIMAGE")
                .build();

        int sequence = 1;
        for (PilgrimageSegmentDto segment : pilgrimage.getSegments()) {
            for (BasePlaceDto spot : segment.getSpots()) {
                route.addWaypoint(UserRouteWaypoint.builder()
                        .sequenceOrder(sequence++)
                        .title(spot.getTitle())
                        .memo(spot.getAddr1())
                        .lat(spot.getLatitude())
                        .lng(spot.getLongitude())
                        .photoUrl(spot.getThumbnailUrl())
                        .build());
            }
        }

        if (route.getWaypoints().isEmpty()) {
            throw new IllegalStateException("이 순례길에는 게시물로 옮길 스팟이 없습니다.");
        }

        return userRouteRepository.save(route).getId();
    }

    @Override
    public List<Coordinate> getWalkingPath(Long routeId) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시물을 찾을 수 없습니다: " + routeId));

        List<UserRouteWaypoint> waypoints = route.getWaypoints();
        if (waypoints.size() < 2) {
            return waypoints.stream()
                    .map(w -> Coordinate.builder().lat(w.getLat()).lng(w.getLng()).build())
                    .collect(Collectors.toList());
        }

        List<UserRouteWaypoint> ordered = nearestNeighborOrder(waypoints);

        if (ordered.size() > MAX_WAYPOINTS_FOR_REAL_ROUTING) {
            return ordered.stream()
                    .map(w -> Coordinate.builder().lat(w.getLat()).lng(w.getLng()).build())
                    .collect(Collectors.toList());
        }

        List<Coordinate> path = new ArrayList<>();
        for (int i = 0; i < ordered.size() - 1; i++) {
            UserRouteWaypoint from = ordered.get(i);
            UserRouteWaypoint to = ordered.get(i + 1);
            List<Coordinate> leg = osrmWalkingDirectionsService.getWalkingPath(
                    from.getLat(), from.getLng(), to.getLat(), to.getLng());

            if (leg.isEmpty()) {
                // 도보 경로 API가 실패하면 최소한 직선으로라도 이어준다.
                path.add(Coordinate.builder().lat(from.getLat()).lng(from.getLng()).build());
                path.add(Coordinate.builder().lat(to.getLat()).lng(to.getLng()).build());
            } else {
                path.addAll(leg);
            }
        }
        return path;
    }

    // 1번(시작) 웨이포인트는 고정하고, 그다음부터는 현재 위치에서 가장 가까운 곳을 계속
    // 골라나가는 탐욕적(nearest-neighbor) 방식으로 실제로 걸을 법한 순서를 만든다.
    private List<UserRouteWaypoint> nearestNeighborOrder(List<UserRouteWaypoint> waypoints) {
        List<UserRouteWaypoint> remaining = new ArrayList<>(waypoints);
        List<UserRouteWaypoint> ordered = new ArrayList<>();

        UserRouteWaypoint current = remaining.remove(0);
        ordered.add(current);

        while (!remaining.isEmpty()) {
            UserRouteWaypoint nearest = null;
            double bestDistance = Double.MAX_VALUE;
            for (UserRouteWaypoint candidate : remaining) {
                double distance = haversineKm(current.getLat(), current.getLng(), candidate.getLat(), candidate.getLng());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest = candidate;
                }
            }
            ordered.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }
        return ordered;
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    @Override
    @Transactional
    public void deleteRoute(Long routeId, String requesterEmail) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + routeId));
        User requester = findUser(requesterEmail);

        if (!route.getAuthor().getId().equals(requester.getId())) {
            throw new AccessDeniedException("본인이 작성한 게시글만 삭제할 수 있습니다.");
        }

        // 댓글/좋아요/스크랩은 UserRoute에 cascade로 걸려있지 않으므로 FK 제약을 피하려면 먼저 지운다.
        // 웨이포인트는 UserRoute의 @OneToMany(cascade=ALL)로 route 삭제 시 함께 삭제된다.
        // (업로드된 사진 파일 자체는 디스크에서 지우지 않는다 — 기존 댓글/좋아요 삭제 시에도 마찬가지로
        // 파일 정리는 하지 않는 패턴을 따름)
        // 대댓글이 부모 댓글을 FK로 참조하므로 대댓글부터 지워야 한다.
        userRouteCommentRepository.deleteByRouteAndParentIsNotNull(route);
        userRouteCommentRepository.deleteByRouteAndParentIsNull(route);
        userRouteLikeRepository.deleteByRoute(route);
        userRouteScrapRepository.deleteByRoute(route);
        userRouteRepository.delete(route);
    }

    @Override
    @Transactional
    public void addWaypoint(Long routeId, String authorEmail, String title, String memo,
                             double lat, double lng, MultipartFile photo) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + routeId));

        User author = findUser(authorEmail);
        if (!route.getAuthor().getId().equals(author.getId())) {
            throw new AccessDeniedException("본인이 작성한 게시글에만 웨이포인트를 추가할 수 있습니다.");
        }

        String photoUrl = fileStorageService.store(photo);

        route.addWaypoint(UserRouteWaypoint.builder()
                .sequenceOrder(route.getWaypoints().size() + 1)
                .title(title)
                .memo(memo)
                .lat(lat)
                .lng(lng)
                .photoUrl(photoUrl)
                .build());
    }

    @Override
    @Transactional
    public boolean toggleLike(Long routeId, String userEmail) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + routeId));
        User user = findUser(userEmail);

        return userRouteLikeRepository.findByUserAndRoute(user, route)
                .map(existing -> {
                    userRouteLikeRepository.delete(existing);
                    return false;
                })
                .orElseGet(() -> {
                    userRouteLikeRepository.save(UserRouteLike.builder().user(user).route(route).build());
                    return true;
                });
    }

    @Override
    @Transactional
    public boolean toggleScrap(Long routeId, String userEmail) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + routeId));
        User user = findUser(userEmail);

        return userRouteScrapRepository.findByUserAndRoute(user, route)
                .map(existing -> {
                    userRouteScrapRepository.delete(existing);
                    return false;
                })
                .orElseGet(() -> {
                    userRouteScrapRepository.save(UserRouteScrap.builder().user(user).route(route).build());
                    return true;
                });
    }

    @Override
    @Transactional
    public UserRouteCommentDto addComment(Long routeId, String authorEmail, String content, Long parentId) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + routeId));
        User author = findUser(authorEmail);

        UserRouteComment parent = null;
        if (parentId != null) {
            parent = userRouteCommentRepository.findById(parentId)
                    .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + parentId));
            if (!parent.getRoute().getId().equals(routeId)) {
                throw new IllegalArgumentException("다른 게시글의 댓글에는 답글을 달 수 없습니다.");
            }
            // 대댓글에 대한 답글은 최상위 댓글 기준으로 평탄화한다 (1단계 스레드만 허용).
            if (parent.getParent() != null) {
                parent = parent.getParent();
            }
        }

        UserRouteComment comment = userRouteCommentRepository.save(UserRouteComment.builder()
                .route(route)
                .author(author)
                .content(content)
                .parent(parent)
                .build());

        return toCommentDto(comment, author);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, String authorEmail) {
        UserRouteComment comment = userRouteCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다: " + commentId));
        User requester = findUser(authorEmail);

        if (!comment.getAuthor().getId().equals(requester.getId())) {
            throw new AccessDeniedException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }

        // 최상위 댓글을 지우면 거기 달린 대댓글도 함께 지운다 (다른 사람이 쓴 답글이어도 함께 삭제됨).
        if (comment.getParent() == null) {
            userRouteCommentRepository.findByParentOrderByCreatedAtAsc(comment)
                    .forEach(userRouteCommentRepository::delete);
        }
        userRouteCommentRepository.delete(comment);
    }

    private UserRouteSummaryDto toSummaryDto(UserRoute route, User currentUser) {
        long likeCount = userRouteLikeRepository.countByRoute(route);
        long commentCount = userRouteCommentRepository.countByRoute(route);
        boolean likedByMe = currentUser != null && userRouteLikeRepository.existsByUserAndRoute(currentUser, route);
        long scrapCount = userRouteScrapRepository.countByRoute(route);
        boolean scrapedByMe = currentUser != null && userRouteScrapRepository.existsByUserAndRoute(currentUser, route);
        String thumbnailUrl = route.getWaypoints().isEmpty() ? "" : route.getWaypoints().get(0).getPhotoUrl();

        return UserRouteSummaryDto.builder()
                .id(route.getId())
                .title(route.getTitle())
                .description(route.getDescription())
                .routeType(route.getRouteType())
                .authorName(route.getAuthor().getDisplayName())
                .authorProfileImageUrl(route.getAuthor().getProfileImageUrl())
                .createdAt(route.getCreatedAt())
                .thumbnailUrl(thumbnailUrl)
                .waypointCount(route.getWaypoints().size())
                .likeCount(likeCount)
                .commentCount(commentCount)
                .likedByMe(likedByMe)
                .scrapCount(scrapCount)
                .scrapedByMe(scrapedByMe)
                .build();
    }

    private UserRouteWaypointDto toWaypointDto(UserRouteWaypoint waypoint) {
        return UserRouteWaypointDto.builder()
                .sequenceOrder(waypoint.getSequenceOrder())
                .title(waypoint.getTitle())
                .memo(waypoint.getMemo())
                .lat(waypoint.getLat())
                .lng(waypoint.getLng())
                .photoUrl(waypoint.getPhotoUrl())
                .build();
    }

    private UserRouteCommentDto toCommentDtoWithReplies(UserRouteComment comment, User currentUser) {
        List<UserRouteCommentDto> replyDtos = userRouteCommentRepository.findByParentOrderByCreatedAtAsc(comment).stream()
                .map(reply -> toCommentDto(reply, currentUser))
                .collect(Collectors.toList());

        return toCommentDtoBuilder(comment, currentUser).replies(replyDtos).build();
    }

    private UserRouteCommentDto toCommentDto(UserRouteComment comment, User currentUser) {
        return toCommentDtoBuilder(comment, currentUser).build();
    }

    private UserRouteCommentDto.UserRouteCommentDtoBuilder toCommentDtoBuilder(UserRouteComment comment, User currentUser) {
        return UserRouteCommentDto.builder()
                .id(comment.getId())
                .authorName(comment.getAuthor().getDisplayName())
                .authorProfileImageUrl(comment.getAuthor().getProfileImageUrl())
                .mine(currentUser != null && comment.getAuthor().getId().equals(currentUser.getId()))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다: " + email));
    }

    private User findUserOrNull(String email) {
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) return null;
        return userRepository.findByEmail(email).orElse(null);
    }
}
