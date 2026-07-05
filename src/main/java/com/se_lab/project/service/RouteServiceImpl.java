package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.CourseDetailDto;
import com.se_lab.project.dto.HomeRecommendDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final TourApiService tourApiService;

    @Override
    public List<BasePlaceDto> getAllRoutes(String category, int page, int size) {
        String contentTypeId = "25"; // Default to '여행코스'

        // TODO: Implement more sophisticated category mapping (e.g., '힐링', '바다' to cat1, cat2, cat3)
        // For now, if a category is provided, we can try to map it to a contentTypeId if applicable.
        // Example: if category.equalsIgnoreCase("culture"), contentTypeId = "12"; (for tourist destinations)
        if (category != null && !category.isEmpty()) {
            switch (category.toLowerCase()) {
                case "tourist_attraction":
                    contentTypeId = "12";
                    break;
                case "culture":
                    contentTypeId = "14"; // 문화시설
                    break;
                case "festival":
                    contentTypeId = "15"; // 축제/공연/행사
                    break;
                case "leports":
                    contentTypeId = "28"; // 레포츠
                    break;
                case "lodging":
                    contentTypeId = "32"; // 숙박
                    break;
                case "shopping":
                    contentTypeId = "38"; // 쇼핑
                    break;
                case "food":
                    contentTypeId = "39"; // 음식점
                    break;
                // Default to 25 if category is not recognized
                default:
                    contentTypeId = "25"; // 여행코스
            }
        }
        List<BasePlaceDto> allPlaces;
        try {
            // 1. 여기서 호출할 때 404가 나면 catch로 바로 점프합니다.
            allPlaces = tourApiService.getPlacesByArea("32", null, contentTypeId, 200);

            // 2. 만약 API는 호출됐는데 데이터가 없으면 빈 리스트로 처리
            if (allPlaces == null) allPlaces = Collections.emptyList();

        } catch (Exception e) {
            // ⭐️ 404가 발생하면 여기서 로그만 남기고 안전하게 빈 리스트를 반환합니다.
            System.out.println("🔥🔥 [경고] API 호출 실패 (데이터가 없거나 URL 오류): " + e.getMessage());
            return Collections.emptyList();
        }

        // Apply pagination (이제 안전하게 아래 로직이 동작합니다)
        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, allPlaces.size());

        if (startIndex < allPlaces.size()) {
            return allPlaces.subList(startIndex, endIndex);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<BasePlaceDto> searchRoutes(String keyword) {
        // Fetch places by keyword. Using a default numOfRows, e.g., 200, to get a broad set of results.
        // Pagination for search results can be implemented here if needed, similar to getAllRoutes.
        return tourApiService.searchByKeyword(keyword, 200);
    }

    @Override
    public CourseDetailDto getRouteDetail(String id) {
        // TODO: Implement actual detail fetching logic. This would typically involve
        // calling an API to get details for a specific content ID.
        // For now, it returns a placeholder CourseDetailDto.
        return new CourseDetailDto("Placeholder Title", "Placeholder Address", 0.0, 0.0, "", "Detailed description of " + id);
    }

    @Override
    public List<HomeRecommendDto> getRandomRecommendRoutes(int count) {
        List<BasePlaceDto> allRoutes = tourApiService.getPlacesByArea("32", null, "25", 200);

        if (allRoutes == null || allRoutes.isEmpty()) return Collections.emptyList(); // 방어코드

        Collections.shuffle(allRoutes);

        return allRoutes.stream()
                .limit(count)
                // ⭐️ 마지막 파라미터 null -> "보통" 으로 수정
                .map(place -> new HomeRecommendDto(place.getTitle(), place.getAddr1(), place.getMapy(), place.getMapx(), place.getThumbnailUrl(), "보통"))
                .collect(Collectors.toList());
    }
}