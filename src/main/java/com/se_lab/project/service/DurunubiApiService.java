package com.se_lab.project.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.se_lab.project.dto.GpxPointDto;
import com.se_lab.project.dto.TrailDto;
import com.se_lab.project.dto.durunubi.DurunubiItem;
import com.se_lab.project.dto.durunubi.DurunubiResponse;
import com.se_lab.project.gpx.GpxParser;
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


    @Value("${walking-course.service-key}")
    private String serviceKey;


    @Value("${walking-course.base-url}")
    private String baseUrl;


    @Value("${walking-course.endpoints.course-list}")
    private String courseListEndpoint;


    public DurunubiApiService(
            RestTemplate restTemplate,
            GpxParser gpxParser
    ) {
        this.restTemplate = restTemplate;
        this.gpxParser = gpxParser;
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


        String xml =
                restTemplate.getForObject(
                        fullUrl,
                        String.class
                );


        log.debug(
                "두루누비 XML 길이={}",
                xml != null ? xml.length() : 0
        );

        List<TrailDto> trails = parseXml(xml);

        trails.forEach(trail ->
                log.info("{} / {}", trail.getRegion(), trail.getCourseName())
        );
        return trails;
    }

    private List<GpxPointDto> getGpxPoints(String url) {

        String gpx =
                restTemplate.getForObject(url, String.class);

        return gpxParser.parse(gpx);
    }

    private List<TrailDto> parseXml(String xml) {

        try {

            DurunubiResponse response =
                    xmlMapper.readValue(xml, DurunubiResponse.class);

            return response.getBody()
                    .getItems()
                    .getItem()
                    .stream()
                    .map(this::convertToTrailDto)
                    .toList();


        } catch (Exception e) {

            log.error("두루누비 XML 파싱 실패", e);

            return List.of();
        }
    }

    private TrailDto convertToTrailDto(DurunubiItem item) {

        List<GpxPointDto> points = List.of();

        try {

            points = getGpxPoints(item.getGpxpath());

            log.info(
                    "코스={}, GPX 좌표 개수={}",
                    item.getCrsKorNm(),
                    points.size()
            );

        } catch (Exception e) {

            log.warn(
                    "GPX 처리 실패 course={}, type={}",
                    item.getCrsKorNm(),
                    e.getClass().getSimpleName()
            );
        }


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
}
