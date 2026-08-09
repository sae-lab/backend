package com.se_lab.project.service;

import com.se_lab.project.dto.GpxPoint;
import com.se_lab.project.dto.TrailDto;
import com.se_lab.project.entity.Trail;
import com.se_lab.project.entity.TrailPoint;
import com.se_lab.project.repository.TrailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class TrailSyncService {

    private final DurunubiApiService durunubiApiService;
    private final TrailRepository trailRepository;

    public void syncTrails() {

        System.out.println("===== SYNC START =====");

        List<TrailDto> trails =
                durunubiApiService.getTrails();

        System.out.println("가져온 코스 수 = " + trails.size());

        for(TrailDto dto : trails){

            if (trailRepository.existsByCourseId(dto.getCourseId())){
                System.out.println("이미 존재 = " + dto.getCourseName());
                continue;
            }


            Trail trail = Trail.builder()
                    .courseId(dto.getCourseId())
                    .courseName(dto.getCourseName())
                    .region(dto.getRegion())
                    .distance(dto.getDistance())
                    .requiredMinutes(dto.getRequiredMinutes())
                    .difficulty(dto.getDifficulty())
                    .gpxUrl(dto.getGpxUrl())
                    .build();



            List<GpxPoint> points =
                    dto.getCoordinates();


            if(points != null && !points.isEmpty()) {



                int sequence = 0;

                for(GpxPoint point : points) {

                    trail.addPoint(
                            TrailPoint.builder()
                                    .sequence(sequence++)
                                    .lat(point.getLat())
                                    .lng(point.getLng())
                                    .build()
                    );

                }

            }

            System.out.println(
                    "좌표 개수 = " + points.size()
            );

            trailRepository.save(trail);

            System.out.println("저장 완료 = " + dto.getCourseName());

        }

    }
}