package com.se_lab.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se_lab.project.dto.BasePlaceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${tour-api.base-url}")
    private String baseUrl;
    @Value("${tour-api.endpoints.location-based}")
    private String locationBasedEndpoint;
    @Value("${tour-api.endpoints.area-based}")
    private String areaBasedEndpoint;
    @Value("${tour-api.service-key}")
    private String serviceKey;
    @Value("${tour-api.endpoints.search-keyword}")
    private String searchKeywordEndpoint;

    public List<BasePlaceDto> getNearbyPlaces(String mapX, String mapY) {
        String fullUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + locationBasedEndpoint)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "KangwonRoad")
                .queryParam("_type", "json")
                .queryParam("mapX", mapX)
                .queryParam("mapY", mapY)
                .queryParam("radius", "2000")
                .queryParam("arrange", "S")
                .queryParam("contentTypeId", "25")
                .queryParam("cat1", "C01")
                .queryParam("cat2", "C0112")
                .build(false)
                .toUriString();

        log.debug("Final URL for getNearbyPlaces: {}", fullUrl);
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
        log.debug("Final URL for getPlacesByArea: {}", fullUrl);
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
        log.debug("Final URL for searchByKeyword: {}", fullUrl);
        return fetchAndParse(fullUrl, "searchByKeyword", false);
    }

    private List<BasePlaceDto> fetchAndParse(String fullUrl, String methodName, boolean useDefaultImage) {
        String jsonString;
        try {
            jsonString = restTemplate.getForObject(fullUrl, String.class);
        } catch (Exception e) {
            log.error("API 네트워크 호출 실패 [{}]: {}", methodName, e.getMessage());
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
        String imageUrl = item.path("firstimage").asText("");
        if (useDefaultImage && imageUrl.isEmpty()) {
            imageUrl = "https://cdn.pixabay.com/photo/2019/08/08/11/33/korea-4392764_1280.jpg";
        }
        return new BasePlaceDto(
                item.path("title").asText(),
                item.path("addr1").asText(),
                item.path("mapy").asDouble(),
                item.path("mapx").asDouble(),
                imageUrl
        );
    }
}
