package com.se_lab.project.controller;

import com.se_lab.project.dto.TrailDto;
import com.se_lab.project.service.DurunubiApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trails")
@RequiredArgsConstructor
public class TrailController {

    private final DurunubiApiService durunubiApiService;

    @GetMapping
    public List<TrailDto> getTrails() {

        return durunubiApiService.getTrails();
    }
}