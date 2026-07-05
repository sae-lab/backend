package com.se_lab.project.controller;

import com.se_lab.project.dto.HomeResponseDto;
import com.se_lab.project.dto.HomeRecommendDto;
import com.se_lab.project.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final RouteService routeService;

    @GetMapping
    public HomeResponseDto getHomeData() {
        // 1. JWT 토큰을 통해 인증된 유저의 이메일(혹은 이름)을 가져옴
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. 서비스에서 로직 처리 (랜덤 3개 추출)
        List<HomeRecommendDto> recommendations = routeService.getRandomRecommendRoutes(3);

        // 3. Wrapper DTO에 담아 반환
        return HomeResponseDto.builder()
                .username(email) // 실제로는 DB에서 email로 이름 조회해서 넣는 게 더 좋아
                .recommendedRoutes(recommendations)
                .build();
    }
}