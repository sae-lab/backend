package com.se_lab.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.se_lab.project.config.CacheConfig;
import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.HomeRecommendDto;
import com.se_lab.project.planner.RoutePlanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(HomeRecommendationCacheTest.TestConfiguration.class)
class HomeRecommendationCacheTest {

    private static final String TOUR_API_RESPONSE = """
            {"response":{"body":{"items":{"item":[
              {"title":"첫 번째","addr1":"강원도","mapy":37.1,"mapx":128.1,"firstimage":"","contentid":"1","contenttypeid":"25"},
              {"title":"두 번째","addr1":"강원도","mapy":37.2,"mapx":128.2,"firstimage":"","contentid":"2","contenttypeid":"25"},
              {"title":"세 번째","addr1":"강원도","mapy":37.3,"mapx":128.3,"firstimage":"","contentid":"3","contenttypeid":"25"},
              {"title":"네 번째","addr1":"강원도","mapy":37.4,"mapx":128.4,"firstimage":"","contentid":"4","contenttypeid":"25"}
            ]}}}}
            """;

    private static final String EMPTY_TOUR_API_RESPONSE = """
            {"response":{"body":{"items":{"item":[]}}}}
            """;

    @BeforeEach
    void resetState(
            @Autowired CacheManager cacheManager,
            @Autowired RestTemplate restTemplate
    ) {
        cacheManager.getCache(CacheConfig.HOME_RECOMMENDATION_CANDIDATES).clear();
        reset(restTemplate);
    }

    @Test
    void cachesCandidateListButCreatesFreshRandomHomeResults(
            @Autowired RouteService routeService,
            @Autowired TourApiService tourApiService,
            @Autowired RestTemplate restTemplate,
            @Autowired CacheManager cacheManager
    ) {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(TOUR_API_RESPONSE);

        List<HomeRecommendDto> first = routeService.getRandomRecommendRoutes(3);
        List<HomeRecommendDto> second = routeService.getRandomRecommendRoutes(3);

        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
        assertThat(first).hasSize(3).isNotSameAs(second);
        assertThat(cacheManager.getCache(CacheConfig.HOME_RECOMMENDATION_CANDIDATES)).isNotNull();
        assertThat(cacheManager.getCache("nearbyPlaces")).isNull();
        assertThat(cacheManager.getCache("placeDetail")).isNull();

        Object cachedCandidates = cacheManager.getCache(CacheConfig.HOME_RECOMMENDATION_CANDIDATES).get("default").get();
        assertThat(cachedCandidates).isInstanceOf(List.class);
        List<?> candidates = (List<?>) cachedCandidates;
        assertThat(candidates).allMatch(candidate -> candidate instanceof BasePlaceDto);
        assertThat(tourApiService.getHomeRecommendationCandidates())
                .extracting(BasePlaceDto::getTitle)
                .containsExactly("첫 번째", "두 번째", "세 번째", "네 번째");
    }

    @Test
    void doesNotCacheEmptyHomeRecommendationCandidates(
            @Autowired RouteService routeService,
            @Autowired RestTemplate restTemplate,
            @Autowired CacheManager cacheManager
    ) {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(EMPTY_TOUR_API_RESPONSE);

        assertThat(routeService.getRandomRecommendRoutes(3)).isEmpty();
        assertThat(routeService.getRandomRecommendRoutes(3)).isEmpty();

        verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
        assertThat(cacheManager.getCache(CacheConfig.HOME_RECOMMENDATION_CANDIDATES).get("default")).isNull();
    }

    @Test
    void coalescesConcurrentNonEmptyCandidateCacheMisses(
            @Autowired TourApiService tourApiService,
            @Autowired RestTemplate restTemplate
    ) throws Exception {
        int requestCount = 8;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch upstreamStarted = new CountDownLatch(1);
        CountDownLatch releaseUpstream = new CountDownLatch(1);

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenAnswer(invocation -> {
            upstreamStarted.countDown();
            assertThat(releaseUpstream.await(5, TimeUnit.SECONDS)).isTrue();
            return TOUR_API_RESPONSE;
        });

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<java.util.concurrent.Future<List<BasePlaceDto>>> results = IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        return tourApiService.getHomeRecommendationCandidates();
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(upstreamStarted.await(5, TimeUnit.SECONDS)).isTrue();
            releaseUpstream.countDown();

            for (java.util.concurrent.Future<List<BasePlaceDto>> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).hasSize(4);
            }
        } finally {
            executor.shutdownNow();
        }

        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }

    @Configuration
    @EnableCaching
    static class TestConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new CacheConfig().cacheManager(java.time.Duration.ofMinutes(30));
        }

        @Bean
        RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        TourApiService tourApiService(RestTemplate restTemplate, CacheManager cacheManager) {
            return new TourApiService(
                    restTemplate,
                    new ObjectMapper(),
                    "https://tour.example.com",
                    "/location",
                    "/area",
                    "test-key",
                    "/search",
                    "/detail",
                    cacheManager
            );
        }

        @Bean
        RouteService routeService(TourApiService tourApiService) {
            return new RouteServiceImpl(tourApiService, mock(RoutePlanner.class));
        }
    }
}
