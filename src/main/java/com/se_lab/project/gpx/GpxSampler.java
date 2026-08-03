package com.se_lab.project.gpx;

import com.se_lab.project.dto.GpxPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GpxSampler {


    public List<GpxPoint> sample(List<GpxPoint> points, int sampleCount) {

        List<GpxPoint> result = new ArrayList<>();

        if (points == null || points.isEmpty()) {
            return result;
        }

        // 좌표 개수가 적으면 그대로 반환
        if (points.size() <= sampleCount) {
            return points;
        }

        if (sampleCount <= 1) {
            return List.of(points.get(0));
        }

        double interval = (double) points.size() / (sampleCount - 1);


        for (int i = 0; i < sampleCount; i++) {

            int index = (int) Math.round(i * interval);

            // index 범위 보호
            if (index >= points.size()) {
                index = points.size() - 1;
            }

            result.add(points.get(index));
        }
        return result;
    }
}