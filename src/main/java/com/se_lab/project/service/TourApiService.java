package com.se_lab.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se_lab.project.constants.TourApiConstants;
import com.se_lab.project.constants.TourTimeConstants;
import com.se_lab.project.dto.BasePlaceDto;
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

    public TourApiService(
            RestTemplate restTemplate,
            ObjectMapper mapper,
            @Value("${tour-api.base-url}") String baseUrl,
            @Value("${tour-api.endpoints.location-based}") String locationBasedEndpoint,
            @Value("${tour-api.endpoints.area-based}") String areaBasedEndpoint,
            @Value("${tour-api.service-key}") String serviceKey,
            @Value("${tour-api.endpoints.search-keyword}") String searchKeywordEndpoint
    ) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.locationBasedEndpoint = locationBasedEndpoint;
        this.areaBasedEndpoint = areaBasedEndpoint;
        this.serviceKey = serviceKey;
        this.searchKeywordEndpoint = searchKeywordEndpoint;
    }

    @Cacheable(value = "nearbyPlaces", key = "#mapX + '_' + #mapY")
    public List<BasePlaceDto> getNearbyPlaces(String mapX, String mapY) {
        String fullUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + locationBasedEndpoint)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "KangwonRoad")
                .queryParam("_type", "json")
                .queryParam("mapX", mapX)
                .queryParam("mapY", mapY)
                .queryParam("radius", "10000")
                .queryParam("numOfRows", "100")
                .queryParam("arrange", "O")
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
