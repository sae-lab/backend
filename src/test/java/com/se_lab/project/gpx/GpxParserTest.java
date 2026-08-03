package com.se_lab.project.gpx;

import com.se_lab.project.dto.GpxPoint;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GpxParserTest {


    @Test
    void parseGpx() throws Exception {

        GpxParser parser = new GpxParser();

        InputStream inputStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("test-trail-route2.gpx");

        assertNotNull(inputStream);

        try (inputStream) {

            String gpxXml = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            List<GpxPoint> points =
                    parser.parse(gpxXml);

            assertFalse(points.isEmpty());
        }
    }

    @Test
    void sampleGpxPoints() throws Exception {

        GpxParser parser = new GpxParser();
        GpxSampler sampler = new GpxSampler();

        InputStream inputStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("test-trail-route2.gpx");

        assertNotNull(inputStream);

        try (inputStream) {

            String gpxXml = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            List<GpxPoint> points =
                    parser.parse(gpxXml);


            List<GpxPoint> sampled =
                    sampler.sample(points, 10);


            System.out.println(
                    "전체 좌표 = " + points.size()
            );


            System.out.println(
                    "대표 좌표 = " + sampled.size()
            );


            sampled.forEach(point ->
                    System.out.println(
                            point.getLatitude()
                                    + ", "
                                    + point.getLongitude()
                    )
            );
        }
    }

}