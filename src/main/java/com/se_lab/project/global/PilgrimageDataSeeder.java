package com.se_lab.project.global;

import com.se_lab.project.entity.PilgrimageRoute;
import com.se_lab.project.entity.PilgrimageSegment;
import com.se_lab.project.repository.PilgrimageRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PilgrimageDataSeeder implements CommandLineRunner {

    private final PilgrimageRouteRepository pilgrimageRouteRepository;

    @Override
    public void run(String... args) {
        if (pilgrimageRouteRepository.count() > 0) return;

        PilgrimageRoute route = PilgrimageRoute.builder()
                .name("강릉-동해-삼척 해안 순례길")
                .description("강릉에서 동해를 거쳐 삼척까지, 동해안을 따라 걷는 구간형 순례 코스")
                .build();

        route.addSegment(PilgrimageSegment.builder()
                .sequenceOrder(1)
                .fromCity("강릉")
                .toCity("동해")
                .fromLat(37.7519).fromLng(128.8761)
                .toLat(37.5247).toLng(129.1143)
                .distanceKm(41.5)
                .difficulty("어려움")
                .estimatedMinutes(620)
                .build());

        route.addSegment(PilgrimageSegment.builder()
                .sequenceOrder(2)
                .fromCity("동해")
                .toCity("삼척")
                .fromLat(37.5247).fromLng(129.1143)
                .toLat(37.4500).toLng(129.1653)
                .distanceKm(16.8)
                .difficulty("보통")
                .estimatedMinutes(252)
                .build());

        pilgrimageRouteRepository.save(route);
    }
}
