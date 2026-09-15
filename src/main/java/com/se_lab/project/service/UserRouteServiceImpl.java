package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.Coordinate;
import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
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
import com.se_lab.project.service.TourSpotLookupService.TourSpot;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRouteServiceImpl implements UserRouteService {

    private final UserRouteRepository userRouteRepository;
    private final UserRouteLikeRepository userRouteLikeRepository;
    private final UserRouteScrapRepository userRouteScrapRepository;
    private final UserRouteCommentRepository userRouteCommentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final PilgrimageService pilgrimageService;
    private final OsrmWalkingDirectionsService osrmWalkingDirectionsService;
    private final NotificationService notificationService;
    private final TourSpotLookupService tourSpotLookupService;

    // 이보다 웨이포인트가 많으면(예: AI 순례길에서 옮겨진 대형 게시물) 다리(leg)마다
    // 외부 도보 경로 API를 부르는 비용이 너무 커져서, 재정렬만 하고 직선으로 잇는다.
    private static final int MAX_WAYPOINTS_FOR_REAL_ROUTING = 20;

    // 목록 표지를 찾으려고 게시물 하나에서 조회할 관광지 수. 목록은 게시물마다 불리므로 작게 둔다.
    private static final int MAX_COVER_LOOKUPS = 2;

    @Override
    public List<UserRouteSummaryDto> getAllRoutes(String currentUserEmail, String routeType, String sort) {
        User currentUser = findUserOrNull(currentUserEmail);
        List<UserRoute> routes = routeType == null
                ? userRouteRepository.findAllByOrderByCreatedAtDesc()
                : userRouteRepository.findAllByRouteTypeOrderByCreatedAtDesc(routeType);
        return sortSummaries(toSummaryDtos(routes, currentUser), sort);
    }

    @Override
    public List<UserRouteSummaryDto> getMyRoutes(String authorEmail, String routeType) {
        User author = findUser(authorEmail);
        List<UserRoute> routes = routeType == null
                ? userRouteRepository.findByAuthorOrderByCreatedAtDesc(author)
                : userRouteRepository.findByAuthorAndRouteTypeOrderByCreatedAtDesc(author, routeType);
        return toSummaryDtos(routes, author);
    }

    @Override
    public List<UserRouteSummaryDto> getMyScraps(String userEmail, String routeType) {
        User user = findUser(userEmail);
        List<UserRoute> routes = userRouteScrapRepository.findByUserOrderByScrapedAtDesc(user).stream()
                .map(UserRouteScrap::getRoute)
                .filter(route -> routeType == null || routeType.equals(route.getRouteType()))
                .toList();
        return toSummaryDtos(routes, user);
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

        // 관광지 웨이포인트는 제목·좌표·사진을 저장하지 않으므로 여기서 실시간으로 채운다.
        List<UserRouteWaypointDto> waypointDtos = resolveWaypoints(route.getWaypoints()).stream()
                .map(this::toWaypointDto)
                .collect(Collectors.toList());

        // 최상위 댓글마다 대댓글을 따로 쿼리하면 댓글 수만큼 쿼리가 늘어나므로,
        // 게시글의 댓글 전체를 한 번에 가져와 부모 id로 메모리에서 묶는다.
        List<UserRouteComment> allComments = userRouteCommentRepository.findByRouteOrderByCreatedAtAsc(route);
        Map<Long, List<UserRouteComment>> repliesByParentId = allComments.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        List<UserRouteCommentDto> commentDtos = allComments.stream()
                .filter(c -> c.getParent() == null)
                .map(comment -> toCommentDtoWithReplies(
                        comment, repliesByParentId.getOrDefault(comment.getId(), List.of()), currentUser))
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
    public Long createFromPilgrimage(Long pilgrimageRouteId, String authorEmail, String routeType, List<String> contentIds) {
        User author = findUser(authorEmail);

        String title;
        String description;
        List<String> spotIds;
        if (contentIds != null) {
            // 사용자가 상세 화면에서 고른 스팟만, 화면에 보인 순서대로 옮긴다.
            // 상세를 다시 조회하지 않는다 — 스팟을 관광 API로 새로 찾느라 느리고,
            // 그 사이 결과가 바뀌면 사용자가 고른 스팟이 빠질 수 있다.
            PilgrimageRouteSummaryDto pilgrimage = pilgrimageService.getRouteSummary(pilgrimageRouteId);
            title = pilgrimage.getName();
            description = pilgrimage.getDescription();
            spotIds = TourSpotLookupService.selectedIds(contentIds);
        } else {
            // 구간별 스팟은 PilgrimageRoute 엔티티 자체엔 저장돼 있지 않고 상세 조회 시 매번
            // 관광 API로 다시 찾아오므로, 그 로직을 그대로 재사용해서 실제 웨이포인트 후보를 얻는다.
            PilgrimageRouteDetailDto pilgrimage = pilgrimageService.getRouteDetail(pilgrimageRouteId);
            title = pilgrimage.getName();
            description = pilgrimage.getDescription();
            spotIds = allSpotIds(pilgrimage);
        }

        UserRoute route = UserRoute.builder()
                .author(author)
                .title(title)
                .description(description)
                .routeType(routeType != null ? routeType : "PILGRIMAGE")
                .build();

        int sequence = 1;
        for (String contentId : spotIds) {
            // 한국관광공사 규정상 관광 데이터는 로컬에 저장하지 않고 실시간으로 호출해야 한다.
            // 그래서 콘텐츠 ID만 남기고, NOT NULL 칸은 관광 정보가 아닌 빈 값으로 채운다.
            // 실제 제목·주소·좌표·사진은 보여줄 때 이 ID로 조회한다.
            route.addWaypoint(UserRouteWaypoint.builder()
                    .sequenceOrder(sequence++)
                    .contentId(contentId)
                    .title("")
                    .lat(0)
                    .lng(0)
                    .build());
        }

        if (route.getWaypoints().isEmpty()) {
            throw new IllegalStateException("이 순례길에는 게시물로 옮길 스팟이 없습니다.");
        }

        return userRouteRepository.save(route).getId();
    }

    // 콘텐츠 ID가 없는 스팟은 나중에 다시 조회할 방법이 없으므로 뺀다.
    private static List<String> allSpotIds(PilgrimageRouteDetailDto pilgrimage) {
        List<String> ids = new ArrayList<>();
        for (PilgrimageSegmentDto segment : pilgrimage.getSegments()) {
            for (BasePlaceDto spot : segment.getSpots()) {
                if (spot.getContentId() != null && !spot.getContentId().isBlank()) {
                    ids.add(spot.getContentId());
                }
            }
        }
        return ids;
    }

    @Override
    public List<Coordinate> getWalkingPath(Long routeId) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시물을 찾을 수 없습니다: " + routeId));

        // 관광지 웨이포인트는 좌표를 저장하지 않으므로, 실시간으로 조회한 좌표로 경로를 만든다.
        List<ResolvedWaypoint> waypoints = resolveWaypoints(route.getWaypoints());
        if (waypoints.size() < 2) {
            return waypoints.stream()
                    .map(w -> Coordinate.builder().lat(w.lat()).lng(w.lng()).build())
                    .collect(Collectors.toList());
        }

        List<ResolvedWaypoint> ordered = nearestNeighborOrder(waypoints);

        if (ordered.size() > MAX_WAYPOINTS_FOR_REAL_ROUTING) {
            return ordered.stream()
                    .map(w -> Coordinate.builder().lat(w.lat()).lng(w.lng()).build())
                    .collect(Collectors.toList());
        }

        List<Coordinate> path = new ArrayList<>();
        for (int i = 0; i < ordered.size() - 1; i++) {
            ResolvedWaypoint from = ordered.get(i);
            ResolvedWaypoint to = ordered.get(i + 1);
            List<Coordinate> leg = osrmWalkingDirectionsService.getWalkingPath(
                    from.lat(), from.lng(), to.lat(), to.lng());

            if (leg.isEmpty()) {
                // 도보 경로 API가 실패하면 최소한 직선으로라도 이어준다.
                path.add(Coordinate.builder().lat(from.lat()).lng(from.lng()).build());
                path.add(Coordinate.builder().lat(to.lat()).lng(to.lng()).build());
            } else {
                path.addAll(leg);
            }
        }
        return path;
    }

    // 1번(시작) 웨이포인트는 고정하고, 그다음부터는 현재 위치에서 가장 가까운 곳을 계속
    // 골라나가는 탐욕적(nearest-neighbor) 방식으로 실제로 걸을 법한 순서를 만든다.
    private List<ResolvedWaypoint> nearestNeighborOrder(List<ResolvedWaypoint> waypoints) {
        List<ResolvedWaypoint> remaining = new ArrayList<>(waypoints);
        List<ResolvedWaypoint> ordered = new ArrayList<>();

        ResolvedWaypoint current = remaining.remove(0);
        ordered.add(current);

        while (!remaining.isEmpty()) {
            ResolvedWaypoint nearest = null;
            double bestDistance = Double.MAX_VALUE;
            for (ResolvedWaypoint candidate : remaining) {
                double distance = GeoUtils.distanceKm(current.lat(), current.lng(), candidate.lat(), candidate.lng());
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
        // 알림이 게시물과 댓글을 FK로 참조하므로 가장 먼저 지운다.
        notificationService.removeForRoute(route);
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

        // 사진은 선택 항목이다. 첨부하지 않으면 저장 단계를 건너뛰고 photoUrl을 비워 둔다.
        String photoUrl = (photo == null || photo.isEmpty()) ? null : storageService.store(photo);

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
    public void deleteWaypoint(Long routeId, String requesterEmail, int sequenceOrder) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + routeId));

        User requester = findUser(requesterEmail);
        if (!route.getAuthor().getId().equals(requester.getId())) {
            throw new AccessDeniedException("본인이 작성한 게시글의 웨이포인트만 삭제할 수 있습니다.");
        }

        // orphanRemoval=true라 컬렉션에서 빼면 행도 함께 지워진다.
        boolean removed = route.getWaypoints().removeIf(w -> w.getSequenceOrder() == sequenceOrder);
        if (!removed) {
            throw new EntityNotFoundException("웨이포인트를 찾을 수 없습니다: " + sequenceOrder);
        }

        // 중간 것을 지우면 번호가 1, 3, 4처럼 비므로 1부터 다시 매긴다.
        int seq = 1;
        for (UserRouteWaypoint waypoint : route.getWaypoints()) {
            waypoint.renumber(seq++);
        }
        // 업로드된 사진 파일 자체는 지우지 않는다. 댓글·좋아요를 지울 때도
        // 파일 정리는 하지 않는 기존 방식을 따른다.
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
                    // 좋아요를 취소하면 알림도 거둔다. 남겨두면 누르지도 않은
                    // 좋아요가 상대 알림함에 계속 보인다.
                    notificationService.removeLikeNotification(route, user);
                    return false;
                })
                .orElseGet(() -> {
                    userRouteLikeRepository.save(UserRouteLike.builder().user(user).route(route).build());
                    notificationService.notifyLike(route, user);
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

        notificationService.notifyComment(route, author, comment);

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
        // 알림이 댓글을 FK로 참조하므로, 댓글보다 알림을 먼저 지워야 제약조건에 걸리지 않는다.
        if (comment.getParent() == null) {
            for (UserRouteComment reply : userRouteCommentRepository.findByParentOrderByCreatedAtAsc(comment)) {
                notificationService.removeForComment(reply);
            }
            userRouteCommentRepository.deleteByParent(comment);
        }
        notificationService.removeForComment(comment);
        userRouteCommentRepository.delete(comment);
    }

    /// 목록 화면의 게시물 요약을 한꺼번에 만든다.
    ///
    /// 게시물마다 좋아요·댓글·저장 수와 "내가 눌렀는지"를 따로 세면 게시물 수 × 5번 쿼리가 나간다.
    /// 목록 전체를 종류마다 한 번씩만 세고, 관광지 표지 사진도 한꺼번에 동시에 조회한다.
    /// 작성자와 웨이포인트는 목록을 가져올 때 함께 불러온다 (UserRouteRepository).
    private List<UserRouteSummaryDto> toSummaryDtos(List<UserRoute> routes, User currentUser) {
        if (routes.isEmpty()) return new ArrayList<>();

        Map<Long, Long> likeCounts = toCountMap(userRouteLikeRepository.countByRoutes(routes));
        Map<Long, Long> commentCounts = toCountMap(userRouteCommentRepository.countByRoutes(routes));
        Map<Long, Long> scrapCounts = toCountMap(userRouteScrapRepository.countByRoutes(routes));
        Set<Long> likedIds = currentUser == null
                ? new HashSet<>()
                : new HashSet<>(userRouteLikeRepository.findRouteIdsByUserAndRoutes(currentUser, routes));
        Set<Long> scrapedIds = currentUser == null
                ? new HashSet<>()
                : new HashSet<>(userRouteScrapRepository.findRouteIdsByUserAndRoutes(currentUser, routes));
        Map<UserRoute, String> covers = coverPhotos(routes);

        List<UserRouteSummaryDto> dtos = new ArrayList<>(routes.size());
        for (UserRoute route : routes) {
            Long id = route.getId();
            dtos.add(UserRouteSummaryDto.builder()
                    .id(id)
                    .title(route.getTitle())
                    .description(route.getDescription())
                    .routeType(route.getRouteType())
                    .authorName(route.getAuthor().getDisplayName())
                    .authorProfileImageUrl(route.getAuthor().getProfileImageUrl())
                    .createdAt(route.getCreatedAt())
                    .thumbnailUrl(covers.getOrDefault(route, ""))
                    .waypointCount(route.getWaypoints().size())
                    .likeCount(likeCounts.getOrDefault(id, 0L))
                    .commentCount(commentCounts.getOrDefault(id, 0L))
                    .likedByMe(likedIds.contains(id))
                    .scrapCount(scrapCounts.getOrDefault(id, 0L))
                    .scrapedByMe(scrapedIds.contains(id))
                    .build());
        }
        return dtos;
    }

    /// [게시물 id, 개수] 행을 id → 개수로 바꾼다. 하나도 없는 게시물은 행이 없다.
    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    /// 표지 후보. 직접 올린 사진 주소이거나, 조회해야 하는 관광지 콘텐츠 ID다.
    private record CoverCandidate(String photoUrl, String contentId) {
    }

    // 목록 표지. 사진 없는 웨이포인트도 허용하므로, 첫 번째가 아니라 사진이 있는 첫 웨이포인트를 쓴다.
    // 관광지 웨이포인트는 사진을 저장하지 않아 조회해야 한다. 게시물마다 앞쪽 관광지 최대
    // MAX_COVER_LOOKUPS곳만 후보로 모아, 목록 전체를 한꺼번에 동시에 조회한다.
    // 끝내 없으면 표지를 넣지 않고 목록 화면이 대체 표지를 그린다.
    private Map<UserRoute, String> coverPhotos(List<UserRoute> routes) {
        Map<UserRoute, List<CoverCandidate>> candidatesByRoute = new IdentityHashMap<>();
        Set<String> contentIds = new LinkedHashSet<>();

        for (UserRoute route : routes) {
            List<CoverCandidate> candidates = new ArrayList<>();
            int tourSpots = 0;
            for (UserRouteWaypoint waypoint : route.getWaypoints()) {
                if (waypoint.isTourSpot()) {
                    if (tourSpots++ < MAX_COVER_LOOKUPS) {
                        candidates.add(new CoverCandidate(null, waypoint.getContentId()));
                        contentIds.add(waypoint.getContentId());
                    }
                    continue;
                }
                String photoUrl = waypoint.getPhotoUrl();
                if (photoUrl != null && !photoUrl.isBlank()) {
                    // 직접 올린 사진이 나오면 그 뒤 웨이포인트는 볼 필요가 없다.
                    candidates.add(new CoverCandidate(photoUrl, null));
                    break;
                }
            }
            candidatesByRoute.put(route, candidates);
        }

        Map<String, TourSpot> spots = contentIds.isEmpty() ? Map.of() : tourSpotLookupService.findAll(contentIds);

        Map<UserRoute, String> covers = new IdentityHashMap<>();
        candidatesByRoute.forEach((route, candidates) -> {
            for (CoverCandidate candidate : candidates) {
                String url = candidate.photoUrl() != null
                        ? candidate.photoUrl()
                        : Optional.ofNullable(spots.get(candidate.contentId())).map(TourSpot::photoUrl).orElse(null);
                if (url != null && !url.isBlank()) {
                    covers.put(route, url);
                    return;
                }
            }
        });
        return covers;
    }

    private UserRouteWaypointDto toWaypointDto(ResolvedWaypoint waypoint) {
        return UserRouteWaypointDto.builder()
                .sequenceOrder(waypoint.sequenceOrder())
                .title(waypoint.title())
                .memo(waypoint.memo())
                .lat(waypoint.lat())
                .lng(waypoint.lng())
                .photoUrl(waypoint.photoUrl())
                .build();
    }

    /// 화면과 경로 계산에 쓰는 웨이포인트 값. 관광지 웨이포인트는 실시간 조회로 채운다.
    private record ResolvedWaypoint(int sequenceOrder, String title, String memo,
                                    double lat, double lng, String photoUrl) {
    }

    /// 웨이포인트 목록을 화면에 쓸 값으로 바꾼다. 원래 순서를 유지한다.
    ///
    /// 관광지 웨이포인트는 상세 정보를 동시에 조회한다. 조회에 실패한 스팟은 결과에서 뺀다 —
    /// 좌표 0,0으로 넘기면 지도에 바다 한가운데 마커가 찍혀 범위가 깨진다.
    private List<ResolvedWaypoint> resolveWaypoints(List<UserRouteWaypoint> waypoints) {
        Map<String, TourSpot> spots = tourSpotLookupService.findAll(waypoints.stream()
                .filter(UserRouteWaypoint::isTourSpot)
                .map(UserRouteWaypoint::getContentId)
                .toList());

        List<ResolvedWaypoint> resolved = new ArrayList<>();
        for (UserRouteWaypoint waypoint : waypoints) {
            if (!waypoint.isTourSpot()) {
                resolved.add(new ResolvedWaypoint(
                        waypoint.getSequenceOrder(), waypoint.getTitle(), waypoint.getMemo(),
                        waypoint.getLat(), waypoint.getLng(), waypoint.getPhotoUrl()));
                continue;
            }
            TourSpot spot = spots.get(waypoint.getContentId());
            if (spot != null) {
                resolved.add(new ResolvedWaypoint(
                        waypoint.getSequenceOrder(), spot.title(), spot.address(),
                        spot.lat(), spot.lng(), spot.photoUrl()));
            }
        }
        return resolved;
    }

    private UserRouteCommentDto toCommentDtoWithReplies(UserRouteComment comment, List<UserRouteComment> replies, User currentUser) {
        List<UserRouteCommentDto> replyDtos = replies.stream()
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
