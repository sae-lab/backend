package com.se_lab.project.gpx;

import com.se_lab.project.dto.GpxPoint;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GpxParserTest {


    @Test
    void parseGpx() throws Exception {

        GpxParser parser = new GpxParser();

        InputStream inputStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("sample2.gpx");


        List<GpxPoint> points =
                parser.parse(inputStream);


        System.out.println(
                "좌표 개수 = " + points.size()
        );


        System.out.println(
                "첫 좌표 = "
                        + points.get(0).getLatitude()
                        + ", "
                        + points.get(0).getLongitude()
        );


        assertFalse(points.isEmpty());
    }


    @Test
    void sampleGpxPoints() throws Exception {

        GpxParser parser = new GpxParser();
        GpxSampler sampler = new GpxSampler();


        InputStream inputStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("sample2.gpx");


        List<GpxPoint> points =
                parser.parse(inputStream);


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