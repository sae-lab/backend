package com.se_lab.project.service;

import com.se_lab.project.dto.CourseDetailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// 한국관광공사 관광지 상세를 콘텐츠 ID로 실시간 조회한다.
///
/// 공사 규정상 관광 데이터는 로컬에 저장하지 않고 보여줄 때마다 호출해야 한다 (#58).
/// 게시물 웨이포인트와 여정 체크포인트는 콘텐츠 ID만 저장하고, 화면에 낼 때 여기서 채운다.
@Slf4j
@Service
@RequiredArgsConstructor
public class TourSpotLookupService {

    // 동시에 몇 건까지 조회할지. 순례길 스팟은 20개 가까이 되는데, 하나씩 부르면 화면이 수십 초 멈춘다.
    private static final int LOOKUP_CONCURRENCY = 6;

    // 데몬 스레드라 서버 종료를 붙잡지 않는다.
    private static final ExecutorService LOOKUP_POOL = Executors.newFixedThreadPool(LOOKUP_CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "tour-spot-lookup");
        thread.setDaemon(true);
        return thread;
    });

    private final TourApiService tourApiService;

    /// 화면과 도착 판정에 쓰는 관광지 값.
    public record TourSpot(String contentId, String title, String address,
                           double lat, double lng, String photoUrl) {
    }

    /// 관광지 하나를 조회한다.
    ///
    /// 조회에 실패하면(네트워크 오류, 공사 데이터에서 없어진 콘텐츠, 좌표 없음) 빈 값을 준다.
    /// 좌표 0,0으로 넘기면 지도에 바다 한가운데 마커가 찍혀 범위가 깨진다.
    public Optional<TourSpot> find(String contentId) {
        if (contentId == null || contentId.isBlank()) return Optional.empty();

        CourseDetailDto detail;
        try {
            detail = tourApiService.getPlaceDetail(contentId);
        } catch (RuntimeException e) {
            detail = null;
        }

        if (detail == null || (detail.getLatitude() == 0 && detail.getLongitude() == 0)) {
            log.warn("관광지 조회 실패, 화면에서 제외 (contentId={})", contentId);
            return Optional.empty();
        }

        return Optional.of(new TourSpot(
                contentId,
                detail.getTitle(),
                detail.getAddr1(),
                detail.getLatitude(),
                detail.getLongitude(),
                detail.getThumbnailUrl()));
    }

    /// 여러 관광지를 동시에 조회한다. 조회에 실패한 ID는 결과에 없다.
    public Map<String, TourSpot> findAll(Collection<String> contentIds) {
        List<String> ids = contentIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        Map<String, CompletableFuture<Optional<TourSpot>>> futures = new LinkedHashMap<>();
        for (String id : ids) {
            futures.put(id, CompletableFuture.supplyAsync(() -> find(id), LOOKUP_POOL));
        }

        Map<String, TourSpot> spots = new HashMap<>();
        futures.forEach((id, future) -> future.join().ifPresent(spot -> spots.put(id, spot)));
        return spots;
    }
}
