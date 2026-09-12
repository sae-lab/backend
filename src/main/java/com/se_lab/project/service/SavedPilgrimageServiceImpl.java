package com.se_lab.project.service;

import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
import com.se_lab.project.entity.PilgrimageRoute;
import com.se_lab.project.entity.PilgrimageSegment;
import com.se_lab.project.entity.SavedPilgrimage;
import com.se_lab.project.entity.User;
import com.se_lab.project.repository.PilgrimageRouteRepository;
import com.se_lab.project.repository.SavedPilgrimageRepository;
import com.se_lab.project.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavedPilgrimageServiceImpl implements SavedPilgrimageService {

    private final SavedPilgrimageRepository savedPilgrimageRepository;
    private final PilgrimageRouteRepository pilgrimageRouteRepository;
    private final UserRepository userRepository;

    @Override
    public List<PilgrimageRouteSummaryDto> getSavedRoutes(String userEmail) {
        User user = findUser(userEmail);
        return savedPilgrimageRepository.findByUserOrderBySavedAtDesc(user).stream()
                .map(saved -> toSummaryDto(saved.getRoute()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean saveRoute(String userEmail, Long routeId) {
        User user = findUser(userEmail);
        PilgrimageRoute route = pilgrimageRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("순례길을 찾을 수 없습니다: " + routeId));

        if (savedPilgrimageRepository.existsByUserAndRoute(user, route)) {
            return false;
        }

        try {
            savedPilgrimageRepository.save(SavedPilgrimage.builder().user(user).route(route).build());
            return true;
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate insert detected - treat as already saved
            return false;
        }
    }

    @Override
    @Transactional
    public boolean unsaveRoute(String userEmail, Long routeId) {
        User user = findUser(userEmail);
        PilgrimageRoute route = pilgrimageRouteRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("순례길을 찾을 수 없습니다: " + routeId));

        return savedPilgrimageRepository.findByUserAndRoute(user, route)
                .map(saved -> {
                    savedPilgrimageRepository.delete(saved);
                    return true;
                })
                .orElse(false);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다: " + email));
    }

    private PilgrimageRouteSummaryDto toSummaryDto(PilgrimageRoute route) {
        List<PilgrimageSegment> segments = route.getSegments();

        String cityChain = segments.isEmpty() ? "" :
                segments.get(0).getFromCity() + segments.stream()
                        .map(s -> " → " + s.getToCity())
                        .collect(Collectors.joining());

        double totalDistance = Math.round(segments.stream().mapToDouble(PilgrimageSegment::getDistanceKm).sum() * 10) / 10.0;
        int totalMinutes = segments.stream().mapToInt(PilgrimageSegment::getEstimatedMinutes).sum();
        String difficulty = segments.stream()
                .map(PilgrimageSegment::getDifficulty)
                .max((a, b) -> difficultyRank(a) - difficultyRank(b))
                .orElse("보통");

        return PilgrimageRouteSummaryDto.builder()
                .id(route.getId())
                .name(route.getName())
                .description(route.getDescription())
                .cityChain(cityChain)
                .totalDistanceKm(totalDistance)
                .totalEstimatedMinutes(totalMinutes)
                .difficulty(difficulty)
                .segmentCount(segments.size())
                .build();
    }

    private int difficultyRank(String difficulty) {
        String d = difficulty == null ? "" : difficulty.toLowerCase(Locale.ROOT);
        if (d.contains("어려움")) return 3;
        if (d.contains("쉬움")) return 1;
        return 2;
    }
}
