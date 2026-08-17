package com.se_lab.project.service;

import com.se_lab.project.dto.UserRouteCommentDto;
import com.se_lab.project.dto.UserRouteDetailDto;
import com.se_lab.project.dto.UserRouteSummaryDto;
import com.se_lab.project.dto.UserRouteWaypointDto;
import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteComment;
import com.se_lab.project.entity.UserRouteLike;
import com.se_lab.project.entity.UserRouteWaypoint;
import com.se_lab.project.repository.UserRouteCommentRepository;
import com.se_lab.project.repository.UserRouteLikeRepository;
import com.se_lab.project.repository.UserRouteRepository;
import com.se_lab.project.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRouteServiceImpl implements UserRouteService {

    private final UserRouteRepository userRouteRepository;
    private final UserRouteLikeRepository userRouteLikeRepository;
    private final UserRouteCommentRepository userRouteCommentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Override
    public List<UserRouteSummaryDto> getAllRoutes(String currentUserEmail) {
        User currentUser = findUserOrNull(currentUserEmail);
        return userRouteRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(route -> toSummaryDto(route, currentUser))
                .collect(Collectors.toList());
    }

    @Override
    public UserRouteDetailDto getRouteDetail(Long id, String currentUserEmail) {
        UserRoute route = userRouteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + id));
        User currentUser = findUserOrNull(currentUserEmail);

        List<UserRouteWaypointDto> waypointDtos = route.getWaypoints().stream()
                .map(this::toWaypointDto)
                .collect(Collectors.toList());

        List<UserRouteCommentDto> commentDtos = userRouteCommentRepository.findByRouteOrderByCreatedAtAsc(route).stream()
                .map(comment -> toCommentDto(comment, currentUser))
                .collect(Collectors.toList());

        long likeCount = userRouteLikeRepository.countByRoute(route);
        boolean likedByMe = currentUser != null && userRouteLikeRepository.existsByUserAndRoute(currentUser, route);
        boolean mine = currentUser != null && route.getAuthor().getId().equals(currentUser.getId());

        return UserRouteDetailDto.builder()
                .id(route.getId())
                .title(route.getTitle())
                .description(route.getDescription())
                .authorName(route.getAuthor().getName())
                .mine(mine)
                .createdAt(route.getCreatedAt())
                .waypoints(waypointDtos)
                .likeCount(likeCount)
                .likedByMe(likedByMe)
                .comments(commentDtos)
                .build();
    }

    @Override
    @Transactional
    public Long createRoute(String authorEmail, String title, String description) {
        User author = findUser(authorEmail);
        UserRoute route = UserRoute.builder()
                .author(author)
                .title(title)
                .description(description)
                .build();
        return userRouteRepository.save(route).getId();
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
    public UserRouteCommentDto addComment(Long routeId, String authorEmail, String content) {
        UserRoute route = userRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다: " + routeId));
        User author = findUser(authorEmail);

        UserRouteComment comment = userRouteCommentRepository.save(UserRouteComment.builder()
                .route(route)
                .author(author)
                .content(content)
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
        userRouteCommentRepository.delete(comment);
    }

    private UserRouteSummaryDto toSummaryDto(UserRoute route, User currentUser) {
        long likeCount = userRouteLikeRepository.countByRoute(route);
        long commentCount = userRouteCommentRepository.countByRoute(route);
        boolean likedByMe = currentUser != null && userRouteLikeRepository.existsByUserAndRoute(currentUser, route);
        String thumbnailUrl = route.getWaypoints().isEmpty() ? "" : route.getWaypoints().get(0).getPhotoUrl();

        return UserRouteSummaryDto.builder()
                .id(route.getId())
                .title(route.getTitle())
                .description(route.getDescription())
                .authorName(route.getAuthor().getName())
                .createdAt(route.getCreatedAt())
                .thumbnailUrl(thumbnailUrl)
                .waypointCount(route.getWaypoints().size())
                .likeCount(likeCount)
                .commentCount(commentCount)
                .likedByMe(likedByMe)
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

    private UserRouteCommentDto toCommentDto(UserRouteComment comment, User currentUser) {
        return UserRouteCommentDto.builder()
                .id(comment.getId())
                .authorName(comment.getAuthor().getName())
                .mine(currentUser != null && comment.getAuthor().getId().equals(currentUser.getId()))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
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
