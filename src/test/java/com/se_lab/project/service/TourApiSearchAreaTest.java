package com.se_lab.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.se_lab.project.config.CacheConfig;
import com.se_lab.project.constants.TourApiConstants;
import com.se_lab.project.dto.BasePlaceDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// 관광지 검색이 강원 밖 결과를 가져오지 않는지 확인한다.
/// 지역을 지정하지 않으면 "부평"에 인천 상점이, "동굴"에 광명동굴이 섞여 나왔다.
class TourApiSearchAreaTest {

    private static final String ONE_PLACE_RESPONSE = """
            {"response":{"body":{"items":{"item":[
              {"title":"용연동굴","addr1":"강원특별자치도 태백시","mapy":37.1,"mapx":128.9,"firstimage":"","contentid":"1","contenttypeid":"12"}
            ]}}}}
            """;

    private final RestTemplate restTemplate = mock(RestTemplate.class);

    private TourApiService tourApiService() {
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(CacheConfig.HOME_RECOMMENDATION_CANDIDATES))
                .thenReturn(new CaffeineCache(CacheConfig.HOME_RECOMMENDATION_CANDIDATES, Caffeine.newBuilder().build()));

        return new TourApiService(
                restTemplate,
                new ObjectMapper(),
                "https://tour.example.com",
                "/locationBasedList2",
                "/areaBasedList2",
                "test-key",
                "/searchKeyword2",
                "/detailCommon2",
                cacheManager
        );
    }

    @Test
    void searchesOnlyWithinGangwon() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(ONE_PLACE_RESPONSE);

        List<BasePlaceDto> places = tourApiService().searchByKeyword("동굴", 200);

        ArgumentCaptor<String> requestedUrl = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForObject(requestedUrl.capture(), eq(String.class));
        assertThat(requestedUrl.getValue()).contains("areaCode=" + TourApiConstants.DEFAULT_AREA_CODE);
        assertThat(places).hasSize(1);
    }
}
