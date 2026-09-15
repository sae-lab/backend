package com.se_lab.project.service;

import com.se_lab.project.dto.BasePlaceDto;
import com.se_lab.project.dto.Coordinate;
import com.se_lab.project.dto.PilgrimageRouteDetailDto;
import com.se_lab.project.dto.PilgrimageRouteSummaryDto;
import com.se_lab.project.dto.PilgrimageSegmentDto;
import com.se_lab.project.dto.UserRouteDetailDto;
import com.se_lab.project.dto.UserRouteSummaryDto;
import com.se_lab.project.dto.UserRouteWaypointDto;
import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteWaypoint;
import com.se_lab.project.repository.UserRepository;
import com.se_lab.project.repository.UserRouteCommentRepository;
import com.se_lab.project.repository.UserRouteLikeRepository;
import com.se_lab.project.repository.UserRouteRepository;
import com.se_lab.project.repository.UserRouteScrapRepository;
import com.se_lab.project.service.TourSpotLookupService.TourSpot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/// 관광공사 규정(로컬 저장 대신 실시간 호출)에 맞춰, AI 순례길에서 옮긴 웨이포인트가
/// 콘텐츠 ID만 저장되고 보여줄 때 조회되는지 확인한다.
///
/// 기존 UserRouteServiceImplTest의 공통 준비 코드는 엄격 모드라, 쓰지 않는 준비가 있으면
/// 실패한다. 준비 내용이 달라서 파일을 분리했다.
@ExtendWith(MockitoExtension.class)
class UserRouteServiceImplTourSpotTest {

    private static final String AUTHOR_EMAIL = "author@example.com";
    private static final String TOUR_PHOTO = "https://tong.visitkorea.or.kr/cms/resource/a.jpg";

    @Mock
    private UserRouteRepository userRouteRepository;
    @Mock
    private UserRouteLikeRepository userRouteLikeRepository;
    @Mock
    private UserRouteScrapRepository userRouteScrapRepository;
    @Mock
    private UserRouteCommentRepository userRouteCommentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private PilgrimageService pilgrimageService;
    @Mock
    private OsrmWalkingDirectionsService osrmWalkingDirectionsService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TourSpotLookupService tourSpotLookupService;

    @InjectMocks
    private UserRouteServiceImpl userRouteService;

    @Test
    void convertingPilgrimageStoresOnlyContentIds() {
        User author = mock(User.class);
        when(userRepository.findByEmail(AUTHOR_EMAIL)).thenReturn(Optional.of(author));

        BasePlaceDto withContentId = BasePlaceDto.builder()
                .title("영랑호").addr1("강원특별자치도 속초시")
                .latitude(38.21).longitude(128.58)
                .thumbnailUrl(TOUR_PHOTO).contentId("126508")
                .build();
        BasePlaceDto withoutContentId = BasePlaceDto.builder()
                .title("ID 없는 곳").latitude(38.10).longitude(128.50).contentId("")
                .build();
        PilgrimageRouteDetailDto pilgrimage = PilgrimageRouteDetailDto.builder()
                .name("속초 순례길").description("설명")
                .segments(List.of(PilgrimageSegmentDto.builder()
                        .spots(List.of(withContentId, withoutContentId))
                        .build()))
                .build();
        when(pilgrimageService.getRouteDetail(7L)).thenReturn(pilgrimage);
        when(userRouteRepository.save(any(UserRoute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userRouteService.createFromPilgrimage(7L, AUTHOR_EMAIL, null, null);

        ArgumentCaptor<UserRoute> saved = ArgumentCaptor.forClass(UserRoute.class);
        verify(userRouteRepository).save(saved.capture());

        // 콘텐츠 ID가 없는 스팟은 다시 조회할 수 없으므로 옮기지 않는다.
        assertThat(saved.getValue().getWaypoints()).singleElement().satisfies(waypoint -> {
            assertThat(waypoint.getContentId()).isEqualTo("126508");
            // 관광 데이터(제목·주소·좌표·사진)는 저장하지 않는다.
            assertThat(waypoint.getTitle()).isEmpty();
            assertThat(waypoint.getMemo()).isNull();
            assertThat(waypoint.getLat()).isZero();
            assertThat(waypoint.getLng()).isZero();
            assertThat(waypoint.getPhotoUrl()).isNull();
        });
        // 저장 시점에는 조회하지 않는다.
        verifyNoInteractions(tourSpotLookupService);
    }

    @Test
    void convertingSelectedSpotsKeepsOnlyThoseInGivenOrder() {
        User author = mock(User.class);
        when(userRepository.findByEmail(AUTHOR_EMAIL)).thenReturn(Optional.of(author));
        when(pilgrimageService.getRouteSummary(7L)).thenReturn(summary());
        when(userRouteRepository.save(any(UserRoute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userRouteService.createFromPilgrimage(7L, AUTHOR_EMAIL, null, List.of("333", "111", "333"));

        ArgumentCaptor<UserRoute> saved = ArgumentCaptor.forClass(UserRoute.class);
        verify(userRouteRepository).save(saved.capture());
        // 화면에서 고른 순서를 따르고 중복은 뺀다.
        assertThat(saved.getValue().getWaypoints())
                .extracting(UserRouteWaypoint::getContentId)
                .containsExactly("333", "111");
        assertThat(saved.getValue().getWaypoints())
                .extracting(UserRouteWaypoint::getSequenceOrder)
                .containsExactly(1, 2);
        assertThat(saved.getValue().getTitle()).isEqualTo("속초 순례길");
        // 고른 스팟을 옮길 때는 스팟을 관광 API로 새로 찾는 느린 상세 조회를 하지 않는다.
        verify(pilgrimageService, never()).getRouteDetail(any());
    }

    @Test
    void convertingWithEmptySelectionIsRejected() {
        User author = mock(User.class);
        when(userRepository.findByEmail(AUTHOR_EMAIL)).thenReturn(Optional.of(author));
        when(pilgrimageService.getRouteSummary(7L)).thenReturn(summary());

        assertThatThrownBy(() -> userRouteService.createFromPilgrimage(7L, AUTHOR_EMAIL, null, List.of("  ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하나 이상");
        verify(userRouteRepository, never()).save(any());
    }

    @Test
    void detailFillsTourSpotsLiveAndDropsOnesThatFailToLoad() {
        UserRoute route = UserRoute.builder().author(mock(User.class)).title("속초 순례길").build();
        route.addWaypoint(UserRouteWaypoint.builder()
                .sequenceOrder(1).title("내가 찍은 곳").memo("메모")
                .lat(38.20).lng(128.59).photoUrl("/uploads/user-routes/me.jpg")
                .build());
        route.addWaypoint(tourWaypoint(2, "126508"));
        route.addWaypoint(tourWaypoint(3, "999999"));
        when(userRouteRepository.findById(1L)).thenReturn(Optional.of(route));
        // 999999는 조회에 실패해 결과에 없다.
        when(tourSpotLookupService.findAll(any())).thenReturn(Map.of(
                "126508", new TourSpot("126508", "영랑호", "강원특별자치도 속초시", 38.21, 128.58, TOUR_PHOTO)));

        UserRouteDetailDto detail = userRouteService.getRouteDetail(1L, null);

        // 조회에 실패한 3번은 빠진다. 좌표 0,0으로 넘기면 지도 범위가 깨진다.
        assertThat(detail.getWaypoints())
                .extracting(UserRouteWaypointDto::getSequenceOrder)
                .containsExactly(1, 2);

        UserRouteWaypointDto own = detail.getWaypoints().get(0);
        assertThat(own.getTitle()).isEqualTo("내가 찍은 곳");
        assertThat(own.getPhotoUrl()).isEqualTo("/uploads/user-routes/me.jpg");

        UserRouteWaypointDto tour = detail.getWaypoints().get(1);
        assertThat(tour.getTitle()).isEqualTo("영랑호");
        assertThat(tour.getMemo()).isEqualTo("강원특별자치도 속초시");
        assertThat(tour.getLat()).isEqualTo(38.21);
        assertThat(tour.getLng()).isEqualTo(128.58);
        assertThat(tour.getPhotoUrl()).isEqualTo(TOUR_PHOTO);
    }

    @Test
    void walkingPathUsesLiveCoordinatesOfTourSpots() {
        UserRoute route = UserRoute.builder().author(mock(User.class)).title("속초 순례길").build();
        route.addWaypoint(tourWaypoint(1, "111"));
        route.addWaypoint(tourWaypoint(2, "222"));
        when(userRouteRepository.findById(1L)).thenReturn(Optional.of(route));
        when(tourSpotLookupService.findAll(any())).thenReturn(Map.of(
                "111", new TourSpot("111", "A", "주소", 38.21, 128.58, TOUR_PHOTO),
                "222", new TourSpot("222", "B", "주소", 38.30, 128.60, TOUR_PHOTO)));
        // 도보 경로 API가 비어 오면 직선으로 잇는다.
        when(osrmWalkingDirectionsService.getWalkingPath(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        List<Coordinate> path = userRouteService.getWalkingPath(1L);

        assertThat(path).extracting(Coordinate::getLat).containsExactly(38.21, 38.30);
        // 저장해 둔 0,0 자리값이 경로에 새면 안 된다.
        assertThat(path).noneMatch(point -> point.getLat() == 0 && point.getLng() == 0);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void feedLooksUpCoversForAllPostsInOneBatchWithAtMostTwoSpotsEach() {
        UserRoute tourRoute = UserRoute.builder().author(mock(User.class)).title("속초 순례길").build();
        tourRoute.addWaypoint(tourWaypoint(1, "111"));
        tourRoute.addWaypoint(tourWaypoint(2, "222"));
        tourRoute.addWaypoint(tourWaypoint(3, "333"));
        UserRoute ownPhotoRoute = UserRoute.builder().author(mock(User.class)).title("집 앞 산책").build();
        ownPhotoRoute.addWaypoint(UserRouteWaypoint.builder()
                .sequenceOrder(1).title("집 앞").lat(38.2).lng(128.5).photoUrl("/uploads/me.jpg").build());
        when(userRouteRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(tourRoute, ownPhotoRoute));
        // 111은 사진을 못 받아왔고 222에서 찾는다.
        when(tourSpotLookupService.findAll(any())).thenReturn(Map.of(
                "222", new TourSpot("222", "B", "주소", 38.21, 128.58, TOUR_PHOTO)));

        List<UserRouteSummaryDto> feed = userRouteService.getAllRoutes(null, null, null);

        assertThat(feed)
                .extracting(UserRouteSummaryDto::getThumbnailUrl)
                .containsExactly(TOUR_PHOTO, "/uploads/me.jpg");
        // 게시물마다 따로 부르지 않고 목록 전체를 한 번에, 게시물당 앞쪽 관광지 두 곳까지만 조회한다.
        ArgumentCaptor<java.util.Collection<String>> ids = (ArgumentCaptor) ArgumentCaptor.forClass(java.util.Collection.class);
        verify(tourSpotLookupService, times(1)).findAll(ids.capture());
        assertThat(ids.getValue()).containsExactly("111", "222");
    }

    private static PilgrimageRouteSummaryDto summary() {
        return PilgrimageRouteSummaryDto.builder().id(7L).name("속초 순례길").description("설명").build();
    }

    private static UserRouteWaypoint tourWaypoint(int sequenceOrder, String contentId) {
        return UserRouteWaypoint.builder()
                .sequenceOrder(sequenceOrder)
                .contentId(contentId)
                .title("")
                .lat(0)
                .lng(0)
                .build();
    }
}
