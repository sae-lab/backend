package com.se_lab.project.service;

import com.se_lab.project.dto.CourseDetailDto;
import com.se_lab.project.service.TourSpotLookupService.TourSpot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourSpotLookupServiceTest {

    private static final String PHOTO = "https://tong.visitkorea.or.kr/cms/resource/a.jpg";

    @Mock
    private TourApiService tourApiService;

    @InjectMocks
    private TourSpotLookupService tourSpotLookupService;

    @Test
    void findFillsSpotFromPlaceDetail() {
        when(tourApiService.getPlaceDetail("126508")).thenReturn(
                new CourseDetailDto("영랑호", "강원특별자치도 속초시", 38.21, 128.58, PHOTO, "126508", "설명"));

        Optional<TourSpot> spot = tourSpotLookupService.find("126508");

        assertThat(spot).contains(new TourSpot("126508", "영랑호", "강원특별자치도 속초시", 38.21, 128.58, PHOTO));
    }

    @Test
    void findIsEmptyWhenLookupFailsOrHasNoCoordinates() {
        when(tourApiService.getPlaceDetail("gone")).thenReturn(null);
        when(tourApiService.getPlaceDetail("no-coordinates")).thenReturn(
                new CourseDetailDto("좌표 없음", "", 0, 0, PHOTO, "no-coordinates", ""));
        when(tourApiService.getPlaceDetail("timeout")).thenThrow(new RuntimeException("timeout"));

        // 좌표 0,0으로 넘기면 지도에 바다 한가운데 마커가 찍힌다.
        assertThat(tourSpotLookupService.find("gone")).isEmpty();
        assertThat(tourSpotLookupService.find("no-coordinates")).isEmpty();
        assertThat(tourSpotLookupService.find("timeout")).isEmpty();
    }

    @Test
    void findSkipsBlankIdWithoutCallingApi() {
        assertThat(tourSpotLookupService.find("")).isEmpty();
        assertThat(tourSpotLookupService.find(null)).isEmpty();
        verifyNoInteractions(tourApiService);
    }

    @Test
    void findAllKeepsOnlyResolvedSpotsAndLooksUpEachIdOnce() {
        when(tourApiService.getPlaceDetail("111")).thenReturn(
                new CourseDetailDto("영랑호", "주소", 38.21, 128.58, PHOTO, "111", ""));
        when(tourApiService.getPlaceDetail("222")).thenReturn(null);

        Map<String, TourSpot> spots = tourSpotLookupService.findAll(List.of("111", "222", "111"));

        assertThat(spots).containsOnlyKeys("111");
        // 같은 스팟이 여러 번 나와도 API는 한 번만 부른다.
        verify(tourApiService, times(1)).getPlaceDetail("111");
    }
}
