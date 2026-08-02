package com.se_lab.project.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RequiredArgsConstructor
@Service("kakaoTravelTimeService")
public class KakaoTravelTimeService implements TravelTimeService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${kakao.api-key}")
    private String apiKey;


    @Override
    public int calculateTravelTime(
            double startX,
            double startY,
            double endX,
            double endY
    ) {

        String origin = startX + "," + startY;
        String destination = endX + "," + endY;


        String url = UriComponentsBuilder
                .fromHttpUrl(
                        "https://apis-navi.kakaomobility.com/v1/directions"
                )
                .queryParam("origin", origin)
                .queryParam("destination", destination)
                .queryParam("priority", "RECOMMEND")
                .toUriString();


        HttpHeaders headers = new HttpHeaders();
        headers.set(
                "Authorization",
                "KakaoAK " + apiKey
        );


        HttpEntity<String> entity =
                new HttpEntity<>(headers);


        try {

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );


            JsonNode root =
                    mapper.readTree(response.getBody());


            JsonNode route =
                    root.path("routes").get(0);


            int distance =
                    route
                            .path("summary")
                            .path("distance")
                            .asInt();


            /*
             * 자동차 시간을 그대로 쓰면 안됨
             * 거리 기반 도보 계산
             */

            double km =
                    distance / 1000.0;


            int walkingTime =
                    (int)Math.ceil(
                            (km / 4.0) * 60
                    );


            log.info(
                    "카카오 거리={}m 도보 예상={}분",
                    distance,
                    walkingTime
            );


            return Math.max(1, walkingTime);


        } catch(Exception e){

            log.error(
                    "카카오 API 실패 fallback",
                    e
            );

            return fallback(
                    startX,
                    startY,
                    endX,
                    endY
            );
        }
    }



    private int fallback(
            double startX,
            double startY,
            double endX,
            double endY
    ){

        double distance =
                calculateDistance(
                        startY,
                        startX,
                        endY,
                        endX
                );


        return Math.max(
                1,
                (int)Math.ceil(
                        distance / 4.0 * 60
                )
        );
    }



    private double calculateDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ){

        final int R = 6371;

        double latDistance =
                Math.toRadians(lat2-lat1);

        double lonDistance =
                Math.toRadians(lon2-lon1);


        double a =
                Math.sin(latDistance/2)
                        *
                        Math.sin(latDistance/2)
                        +
                        Math.cos(Math.toRadians(lat1))
                                *
                                Math.cos(Math.toRadians(lat2))
                                *
                                Math.sin(lonDistance/2)
                                *
                                Math.sin(lonDistance/2);


        double c =
                2*Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1-a)
                );


        return R*c;
    }
}