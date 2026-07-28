package com.se_lab.project.service;

import com.se_lab.project.constants.TourApiConstants;
import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.CourseDetailDto;
import com.se_lab.project.dto.HomeRecommendDto;
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

    private final TourApiService tourApiService;

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

    private String mapCategoryToContentTypeId(String category) {
        if (category == null || category.isEmpty()) return TourApiConstants.DEFAULT_CONTENT_TYPE_ID;

        switch (category.toLowerCase()) {
            case "tourist_attraction": return TourApiConstants.CONTENT_TYPE_TOURIST_ATTRACTION;
            case "culture": return TourApiConstants.CONTENT_TYPE_CULTURE;
            case "festival": return TourApiConstants.CONTENT_TYPE_FESTIVAL;
            case "leports": return TourApiConstants.CONTENT_TYPE_LEPORTS;
            case "lodging": return TourApiConstants.CONTENT_TYPE_LODGING;
            case "shopping": return TourApiConstants.CONTENT_TYPE_SHOPPING;
            case "food": return TourApiConstants.CONTENT_TYPE_FOOD;
            default: return TourApiConstants.DEFAULT_CONTENT_TYPE_ID;
        }
    }
}
