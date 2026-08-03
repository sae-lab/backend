package com.se_lab.project.service;

import com.se_lab.project.dto.TrailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrailServiceImpl implements TrailService {


    private final DurunubiApiService durunubiApiService;


    @Override
    public void syncTrails() {

        log.info("두루누비 데이터 동기화 시작");


        List<TrailDto> trails =
                durunubiApiService.getTrails();


        log.info(
                "조회된 코스 개수 = {}",
                trails.size()
        );


        log.info("두루누비 데이터 동기화 완료");
    }
}