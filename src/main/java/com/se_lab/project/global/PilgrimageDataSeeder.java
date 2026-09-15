package com.se_lab.project.global;

import com.se_lab.project.entity.PilgrimageRoute;
import com.se_lab.project.entity.PilgrimageSegment;
import com.se_lab.project.repository.PilgrimageRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PilgrimageDataSeeder implements CommandLineRunner {

    private final PilgrimageRouteRepository pilgrimageRouteRepository;

    @Override
    public void run(String... args) {
        // 두루누비 코스는 더 이상 부팅 때 DB로 받아오지 않는다.
        // 한국관광공사 규정상 실시간 호출이 가능한 데이터는 로컬에 저장할 수 없다 (#58).

        String identifier = "seed-gangneung-donghae-samcheok";

        if (pilgrimageRouteRepository.existsByIdentifier(identifier)) {
            return;
        }

        PilgrimageRoute route = PilgrimageRoute.builder()
                .identifier(identifier)
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
