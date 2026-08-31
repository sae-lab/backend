package com.se_lab.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se_lab.project.constants.TourApiConstants;
import com.se_lab.project.constants.TourTimeConstants;
import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.CourseDetailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TourApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    private final String baseUrl;
    private final String locationBasedEndpoint;
    private final String areaBasedEndpoint;
    private final String serviceKey;
    private final String searchKeywordEndpoint;
    private final String detailCommonEndpoint;

    public TourApiService(
            RestTemplate restTemplate,
            ObjectMapper mapper,
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.endpoints.location-based}") String locationBasedEndpoint,
            @Value("${tour-api.endpoints.area-based}") String areaBasedEndpoint,
            @Value("${tour-api.service-key}") String serviceKey,
            @Value("${tour-api.endpoints.search-keyword}") String searchKeywordEndpoint,
            @Value("${tour-api.endpoints.detail-common}") String detailCommonEndpoint
    ) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.locationBasedEndpoint = locationBasedEndpoint;
        this.areaBasedEndpoint = areaBasedEndpoint;
        this.serviceKey = serviceKey;
        this.searchKeywordEndpoint = searchKeywordEndpoint;
        this.detailCommonEndpoint = detailCommonEndpoint;
    }

    @Cacheable(value = "nearbyPlaces", key = "#mapX + '_' + #mapY")
    public List<BasePlaceDto> getNearbyPlaces(String mapX, String mapY) {
        return getNearbyPlaces(mapX, mapY, null);
    }

    // contentTypeId가 주어지면 해당 카테고리로만 필터링 (순례길 자동생성 카테고리 선택용)
    public List<BasePlaceDto> getNearbyPlaces(String mapX, String mapY, String contentTypeId) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + locationBasedEndpoint)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "KangwonRoad")
                .queryParam("_type", "json")
                .queryParam("mapX", mapX)
                .queryParam("mapY", mapY)
                .queryParam("radius", "10000")
                .queryParam("numOfRows", "100")
                .queryParam("arrange", "O");

        if (contentTypeId != null && !contentTypeId.isEmpty()) {
            uriBuilder.queryParam("contentTypeId", contentTypeId);
        }

        String fullUrl = uriBuilder.build(false).toUriString();
        return fetchAndParse(fullUrl, "getNearbyPlaces", true);
    }

    public List<BasePlaceDto> getPlacesByArea(String areaCode, String sigunguCode, String contentTypeId, int numOfRows) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + areaBasedEndpoint)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "WEB")
                .queryParam("MobileApp", "KangwonRoad")
                .queryParam("_type", "json")
                .queryParam("numOfRows", String.valueOf(numOfRows))
                .queryParam("arrange", "O");

        if (areaCode != null && !areaCode.isEmpty()) uriBuilder.queryParam("areaCode", areaCode);
        if (sigunguCode != null && !sigunguCode.isEmpty()) uriBuilder.queryParam("sigunguCode", sigunguCode);
        if (contentTypeId != null && !contentTypeId.isEmpty()) uriBuilder.queryParam("contentTypeId", contentTypeId);

        String fullUrl = uriBuilder.build(false).toUriString();
        return fetchAndParse(fullUrl, "getPlacesByArea", false);
    }

    public List<BasePlaceDto> searchByKeyword(String keyword, int numOfRows) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + searchKeywordEndpoint)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "KangwonRoad")
                .queryParam("_type", "json")
                .queryParam("keyword", keyword)
                .queryParam("numOfRows", String.valueOf(numOfRows))
                .queryParam("arrange", "A");

        String fullUrl = uriBuilder.build(false).toUriString();
        return fetchAndParse(fullUrl, "searchByKeyword", false);
    }

    // contentId 단건 상세 정보(설명글 포함)를 조회한다.
    // 주의: 이 API 버전(KorService2)의 detailCommon2는 defaultYN/firstImageYN 같은
    // 부가 플래그나 contentTypeId를 넘기면 INVALID_REQUEST_PARAMETER_ERROR를 낸다.
    // contentId만 넘겨도 overview/mapx/mapy/firstimage가 기본으로 포함되어 온다.
    public CourseDetailDto getPlaceDetail(String contentId) {
        String fullUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + detailCommonEndpoint)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "KangwonRoad")
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .build(false).toUriString();
        log.debug("Final URL for getPlaceDetail: {}", fullUrl);

        String jsonString;
        try {
            jsonString = restTemplate.getForObject(fullUrl, String.class);
        } catch (Exception e) {
            log.error("API 네트워크 호출 실패 [getPlaceDetail]: {}", e.getMessage());
            return null;
        }

        try {
            JsonNode root = mapper.readTree(jsonString);
            JsonNode item = root.path("response").path("body").path("items").path("item");
            if (item.isArray()) item = item.get(0);
            if (item == null || item.isMissingNode()) return null;

            String imageUrl = item.path("firstimage").asText("");
            if (imageUrl.isEmpty()) {
                imageUrl = "https://cdn.pixabay.com/photo/2019/08/08/11/33/korea-4392764_1280.jpg";
            }

            return new CourseDetailDto(
                    item.path("title").asText(),
                    item.path("addr1").asText(),
                    item.path("mapy").asDouble(),
                    item.path("mapx").asDouble(),
                    imageUrl,
                    contentId,
                    item.path("overview").asText("")
            );
        } catch (Exception e) {
            log.error("JSON 파싱 실패 [getPlaceDetail]: {}", e.getMessage());
            return null;
        }
    }

    private List<BasePlaceDto> fetchAndParse(String fullUrl, String methodName, boolean useDefaultImage) {
        String jsonString;
        try {
            jsonString = restTemplate.getForObject(fullUrl, String.class);
        } catch (Exception e) {
            log.warn("관광 API 호출 실패 [{}], type={}", methodName, e.getClass().getSimpleName());
            return new ArrayList<>();
        }

        return parseJson(jsonString, methodName, useDefaultImage);
    }

    private List<BasePlaceDto> parseJson(String jsonString, String methodName, boolean useDefaultImage) {
        List<BasePlaceDto> result = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(jsonString);
            JsonNode items = root.path("response").path("body").path("items").path("item");

            if (items.isArray()) {
                for (JsonNode item : items) result.add(createBasePlaceDto(item, useDefaultImage));
            } else if (items.isObject()) {
                result.add(createBasePlaceDto(items, useDefaultImage));
            }
        } catch (Exception e) {
            log.error("JSON 파싱 실패 [{}]: {}", methodName, e.getMessage());
        }
        return result;
    }

    private BasePlaceDto createBasePlaceDto(JsonNode item, boolean useDefaultImage) {

        String title = item.path("title").asText();
        String contentTypeId = item.path("contenttypeid").asText();

        log.info(
                "관광 데이터: {} / type={} / cat1={} / cat2={} / cat3={}",
                title,
                contentTypeId,
                item.path("cat1").asText(),
                item.path("cat2").asText(),
                item.path("cat3").asText()
        );

        String imageUrl = item.path("firstimage").asText("");
        if (useDefaultImage && imageUrl.isEmpty()) {
            imageUrl = "https://cdn.pixabay.com/photo/2019/08/08/11/33/korea-4392764_1280.jpg";
        }

        return new BasePlaceDto(
                title,
                item.path("addr1").asText(),
                item.path("mapy").asDouble(),
                item.path("mapx").asDouble(),
                imageUrl,
                item.path("contentid").asText(),
                contentTypeId,
                item.path("cat1").asText(),
                item.path("cat2").asText(),
                item.path("cat3").asText(),
                TourTimeConstants.getStayTime(contentTypeId)
        );
    }
}
