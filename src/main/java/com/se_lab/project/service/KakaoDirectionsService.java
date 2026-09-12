package com.se_lab.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.se_lab.project.dto.Coordinate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * 카카오모빌리티 길찾기 API에서 실제 도로를 따라가는 경로 좌표를 가져온다.
 * 도보 전용 API는 카카오가 공개로 제공하지 않아 자동차 경로를 사용하지만,
 * 직선보다 훨씬 실제 지형/도로에 가까운 경로를 보여줄 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoDirectionsService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${kakao.api-key}")
    private String apiKey;

    public List<Coordinate> getRoutePath(double fromLat, double fromLng, double toLat, double toLng) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://apis-navi.kakaomobility.com/v1/directions")
                .queryParam("origin", fromLng + "," + fromLat)
                .queryParam("destination", toLng + "," + toLat)
                .queryParam("priority", "RECOMMEND")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + apiKey);
        // RestTemplateConfig가 등록한 StringHttpMessageConverter(text/plain)가 기본 Accept
        // 헤더의 우선순위를 바꿔놔서, 명시하지 않으면 카카오가 JSON 대신 HTML 에러 응답을 준다.
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parsePath(response.getBody());
        } catch (Exception e) {
            log.warn("카카오 길찾기 경로 조회 실패 ({},{} -> {},{}): {}", fromLat, fromLng, toLat, toLng, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Coordinate> parsePath(String responseBody) {
        List<Coordinate> path = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode route = root.path("routes").get(0);
            if (route == null || route.path("result_code").asInt(-1) != 0) {
                return Collections.emptyList();
            }

            for (JsonNode section : route.path("sections")) {
                for (JsonNode road : section.path("roads")) {
                    JsonNode vertexes = road.path("vertexes");
                    // vertexes는 [lng, lat, lng, lat, ...] 형태의 평탄화된 배열
                    for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                        double lng = vertexes.get(i).asDouble();
                        double lat = vertexes.get(i + 1).asDouble();
                        path.add(Coordinate.builder().lat(lat).lng(lng).build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("카카오 길찾기 응답 파싱 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
        return path;
    }
}
