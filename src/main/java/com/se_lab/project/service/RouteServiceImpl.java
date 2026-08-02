package com.se_lab.project.service;

import com.se_lab.project.constants.TourApiConstants;
import com.se_lab.project.constants.TourTimeConstants;
import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.CourseDetailDto;
import com.se_lab.project.dto.HomeRecommendDto;
import com.se_lab.project.planner.RoutePlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private boolean isWalkingPlace(BasePlaceDto place) {

        String title = place.getTitle();

        String cat1 = place.getCat1();
        String cat2 = place.getCat2();

        return "A01".equals(cat1)       // 자연
                || "A0202".equals(cat2) // 자연 관광지
                || title.contains("공원")
                || title.contains("해변")
                || title.contains("호수")
                || title.contains("둘레길")
                || title.contains("산책")
                || title.contains("숲");
    }

    private final TourApiService tourApiService;
    private final RoutePlanner routePlanner;

    @Override
    public List<BasePlaceDto> getAllRoutes(String category, int page, int size) {
        String contentTypeId = mapCategoryToContentTypeId(category);

        List<BasePlaceDto> allPlaces;
        try {
            allPlaces = tourApiService.getPlacesByArea(TourApiConstants.DEFAULT_AREA_CODE, null, contentTypeId, 200);
            if (allPlaces == null) allPlaces = Collections.emptyList();
        } catch (Exception e) {
            log.error("API 데이터 조회 실패: {}", e.getMessage());
            return Collections.emptyList();
        }

        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, allPlaces.size());

        return (startIndex < allPlaces.size()) ? allPlaces.subList(startIndex, endIndex) : Collections.emptyList();
    }

    @Override
    public List<BasePlaceDto> searchRoutes(String keyword) {
        return tourApiService.searchByKeyword(keyword, 200);
    }

    @Override
    public CourseDetailDto getRouteDetail(String id) {
        return new CourseDetailDto("Placeholder Title", "Placeholder Address", 0.0, 0.0, "", id, "Detailed description of " + id);
    }

    @Override
    public List<HomeRecommendDto> getRandomRecommendRoutes(int count) {
        List<BasePlaceDto> allRoutes = tourApiService.getPlacesByArea(
                TourApiConstants.DEFAULT_AREA_CODE, null, TourApiConstants.DEFAULT_CONTENT_TYPE_ID, 200);

        if (allRoutes == null || allRoutes.isEmpty()) return Collections.emptyList();

        Collections.shuffle(allRoutes);

        return allRoutes.stream()
                .limit(count)
                .map(place -> new HomeRecommendDto(place.getTitle(), place.getAddr1(), place.getMapy(), place.getMapx(), place.getThumbnailUrl(), place.getContentId(), "보통"))
                .collect(Collectors.toList());
    }

    @Override
    public List<BasePlaceDto> getOptimalRoute(
            double longitude,
            double latitude,
            int minutes) {
        // 1. 내 주변(현재 좌표 x, y)의 관광지/코스 후보 조회 (반경 10km 내외)
        List<BasePlaceDto> candidates = tourApiService.getNearbyPlaces(
                String.valueOf(longitude),
                String.valueOf(latitude));

        logNearbyCandidateCount(candidates);

        // 여기 추가
        for (BasePlaceDto c : candidates) {
            log.info(
                    "API 후보: {} / type={} / cat1={} / cat2={} / cat3={}",
                    c.getTitle(),
                    c.getContentTypeId(),
                    c.getCat1(),
                    c.getCat2(),
                    c.getCat3()
            );
        }

        candidates = filterWalkingCandidates(candidates);
        candidates.forEach(p -> p.setEstimatedStayTime(TourTimeConstants.getStayTime("tourist_attraction")));
        
        // 로그

        log.info("필터 이후 후보 개수: {}", candidates.size());

        for(BasePlaceDto c : candidates){
            log.info("필터 통과: {}", c.getTitle());
        }
        
        // 로그

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 플래너를 통해 가용 시간(minutes)에 최적화된 경로 생성
        List<BasePlaceDto> result = routePlanner.generateOptimalRoute(longitude, latitude, minutes, candidates);

        logGeneratedRouteCount(result);
        return result;
    }

    private void logNearbyCandidateCount(List<BasePlaceDto> candidates) {
        log.info("내 주변 조회된 후보지 개수: {}", candidates != null ? candidates.size() : 0);
    }

    private void logGeneratedRouteCount(List<BasePlaceDto> result) {
        log.info("생성된 최종 경로 개수: {}", result.size());
    }

    private List<BasePlaceDto> filterWalkingCandidates(List<BasePlaceDto> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        for (BasePlaceDto place : candidates) {
            log.info(
                    "후보 확인: {} / type={} / cat1={} / cat2={} / cat3={}",
                    place.getTitle(),
                    place.getContentTypeId(),
                    place.getCat1(),
                    place.getCat2(),
                    place.getCat3()
            );
        }

        return candidates.stream()
                .filter(this::isWalkingPlace)
                .collect(Collectors.toList());
    }

    private String mapCategoryToContentTypeId(String category) {
        if (category == null || category.isEmpty()) return TourApiConstants.DEFAULT_CONTENT_TYPE_ID;

        return switch (category.toLowerCase()) {
            case "tourist_attraction" -> TourApiConstants.CONTENT_TYPE_TOURIST_ATTRACTION;
            case "culture" -> TourApiConstants.CONTENT_TYPE_CULTURE;
            case "festival" -> TourApiConstants.CONTENT_TYPE_FESTIVAL;
            case "leports" -> TourApiConstants.CONTENT_TYPE_LEPORTS;
            case "lodging" -> TourApiConstants.CONTENT_TYPE_LODGING;
            case "shopping" -> TourApiConstants.CONTENT_TYPE_SHOPPING;
            case "food" -> TourApiConstants.CONTENT_TYPE_FOOD;
            default -> TourApiConstants.DEFAULT_CONTENT_TYPE_ID;
        };
    }
}
