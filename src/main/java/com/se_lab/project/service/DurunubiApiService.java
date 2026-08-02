package com.se_lab.project.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.se_lab.project.dto.GpxPoint;
import com.se_lab.project.dto.TrailDto;
import com.se_lab.project.dto.durunubi.DurunubiResponse;
import com.se_lab.project.gpx.GpxParser;
import com.se_lab.project.gpx.GpxSampler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Slf4j
@Service
public class DurunubiApiService {

    private final RestTemplate restTemplate;
    private final XmlMapper xmlMapper = new XmlMapper();
    private final GpxParser gpxParser;
    private final GpxSampler gpxSampler;


    @Value("${walking-course.service-key}")
    private String serviceKey;


    @Value("${walking-course.base-url}")
    private String baseUrl;


    @Value("${walking-course.endpoints.course-list}")
    private String courseListEndpoint;


    public DurunubiApiService(
            RestTemplate restTemplate,
            GpxParser gpxParser,
            GpxSampler gpxSampler
    ) {
        this.restTemplate = restTemplate;
        this.gpxParser = gpxParser;
        this.gpxSampler = gpxSampler;
    }


    public List<TrailDto> getTrails() {

        String fullUrl = UriComponentsBuilder
                .fromHttpUrl(baseUrl + courseListEndpoint)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "sightseeing_project")
                .queryParam("numOfRows", "100")
                .queryParam("pageNo", "1")
                .queryParam("brdDiv", "DNWW")
                .build(false)
                .toUriString();


        log.info(
                "두루누비 요청 fullUrl={}",
                fullUrl
        );


        String xml =
                restTemplate.getForObject(
                        fullUrl,
                        String.class
                );


        log.info(
                "두루누비 XML 길이={}",
                xml != null ? xml.length() : 0
        );


        if (xml != null && !xml.isEmpty()) {
            log.info(
                    xml.substring(0, Math.min(xml.length(), 500))
            );
        }
        return parseXml(xml);
    }

    private List<GpxPoint> getGpxPoints(String url) {

        String gpx =
                restTemplate.getForObject(
                        url,
                        String.class
                );

        List<GpxPoint> points =
                gpxParser.parse(gpx);

        return gpxSampler.sample(points, 100);
    }

    private List<TrailDto> parseXml(String xml) {

        try {

            DurunubiResponse response =
                    xmlMapper.readValue(
                            xml,
                            DurunubiResponse.class
                    );

            List<TrailDto> result = response
                    .getBody()
                    .getItems()
                    .getItem()
                    .stream()

                    .map(item -> {

                        List<GpxPoint> points = List.of();

                        try {

                            points = getGpxPoints(item.getGpxpath());

                            return TrailDto.builder()
                                    .routeId(item.getRouteIdx())
                                    .courseId(item.getCrsIdx())
                                    .courseName(item.getCrsKorNm())
                                    .region(item.getSigun())
                                    .distance(Double.parseDouble(item.getCrsDstnc()))
                                    .requiredMinutes(Integer.parseInt(item.getCrsTotlRqrmHour()))
                                    .difficulty(Integer.parseInt(item.getCrsLevel()))
                                    .courseType(item.getCrsCycle())
                                    .summary(item.getCrsSummary())
                                    .gpxUrl(item.getGpxpath())
                                    .coordinates(points)
                                    .build();

                        } catch (Exception e) {

                            log.warn(
                                    "GPX 처리 실패 course={}, url={}",
                                    item.getCrsKorNm(),
                                    item.getGpxpath(),
                                    e
                            );

                            return TrailDto.builder()
                                    .routeId(item.getRouteIdx())
                                    .courseId(item.getCrsIdx())
                                    .courseName(item.getCrsKorNm())
                                    .region(item.getSigun())
                                    .distance(Double.parseDouble(item.getCrsDstnc()))
                                    .requiredMinutes(Integer.parseInt(item.getCrsTotlRqrmHour()))
                                    .difficulty(Integer.parseInt(item.getCrsLevel()))
                                    .courseType(item.getCrsCycle())
                                    .summary(item.getCrsSummary())
                                    .gpxUrl(item.getGpxpath())
                                    .coordinates(points)
                                    .build();
                        }

                    })
                    .toList();

            log.info("파싱된 코스 개수={}", result.size());
            if (!result.isEmpty()) {

                TrailDto first = result.get(0);

                log.info(
                        "첫 코스={}, 좌표수={}",
                        first.getCourseName(),
                        first.getCoordinates().size()
                );
            }


            // ⭐ 여기 추가
            // testGpx(result.get(0).getGpxUrl());


            return result;

        } catch (Exception e) {

            log.error("두루누비 XML 파싱 실패", e);

            return List.of();
        }
    }
}