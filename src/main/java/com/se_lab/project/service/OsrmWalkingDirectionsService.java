package com.se_lab.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se_lab.project.dto.Coordinate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OSRM 공개 데모 서버(router.project-osrm.org)의 "foot"(도보) 프로필로
 * 실제 걸을 수 있는 경로 좌표를 가져온다. 카카오모빌리티는 자동차 경로만
 * 공개로 제공해서, 진짜 도보 경로가 필요한 순례길 지도 표시에는 이걸 쓴다.
 *
 * 주의: OSRM의 duration/distance 값은 이 공개 데모 서버 기준으로 신뢰하기
 * 어려워(도보 프로파일 설정이 지역에 따라 부정확할 수 있음) 좌표(geometry)만
 * 사용하고, 실제 거리/난이도/소요시간은 기존처럼 직선거리 기반으로 계산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OsrmWalkingDirectionsService {

    private static final String BASE_URL = "https://router.project-osrm.org/route/v1/foot";

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Coordinate> getWalkingPath(double fromLat, double fromLng, double toLat, double toLng) {
        String url = UriComponentsBuilder
                .fromHttpUrl(BASE_URL + "/" + fromLng + "," + fromLat + ";" + toLng + "," + toLat)
                .queryParam("overview", "full")
                .queryParam("geometries", "geojson")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parsePath(response.getBody());
        } catch (Exception e) {
            log.warn("OSRM 도보 경로 조회 실패 ({},{} -> {},{}): {}", fromLat, fromLng, toLat, toLng, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Coordinate> parsePath(String responseBody) {
        List<Coordinate> path = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(responseBody);
            if (!"Ok".equals(root.path("code").asText())) {
                return Collections.emptyList();
            }

            JsonNode coordinates = root.path("routes").get(0).path("geometry").path("coordinates");
            // GeoJSON은 [lng, lat] 순서
            for (JsonNode point : coordinates) {
                double lng = point.get(0).asDouble();
                double lat = point.get(1).asDouble();
                path.add(Coordinate.builder().lat(lat).lng(lng).build());
            }
        } catch (Exception e) {
            log.warn("OSRM 응답 파싱 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
        return path;
    }
}
