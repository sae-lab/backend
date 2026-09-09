package com.se_lab.project.service;

import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteWaypoint;
import com.se_lab.project.repository.UserRepository;
import com.se_lab.project.repository.UserRouteCommentRepository;
import com.se_lab.project.repository.UserRouteLikeRepository;
import com.se_lab.project.repository.UserRouteRepository;
import com.se_lab.project.repository.UserRouteScrapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRouteServiceImplTest {

    private static final long ROUTE_ID = 1L;
    private static final String AUTHOR_EMAIL = "author@example.com";

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

    @InjectMocks
    private UserRouteServiceImpl userRouteService;

    private UserRoute route;

    @BeforeEach
    void setUp() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(10L);
        route = UserRoute.builder()
                .author(author)
                .title("산책 경로")
                .build();

        when(userRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));
        when(userRepository.findByEmail(AUTHOR_EMAIL)).thenReturn(Optional.of(author));
    }

    @Test
    void addsWaypointWithNullPhotoWithoutUploading() {
        userRouteService.addWaypoint(
                ROUTE_ID, AUTHOR_EMAIL, "이미지 없는 장소", null, 37.1, 127.1, null);

        assertThat(route.getWaypoints()).singleElement()
                .extracting(UserRouteWaypoint::getPhotoUrl)
                .isNull();
        verifyNoInteractions(storageService);
    }

    @Test
    void addsWaypointWithEmptyPhotoWithoutUploading() {
        MockMultipartFile emptyPhoto = new MockMultipartFile("photo", new byte[0]);

        userRouteService.addWaypoint(
                ROUTE_ID, AUTHOR_EMAIL, "빈 파일인 장소", "메모", 37.2, 127.2, emptyPhoto);

        assertThat(route.getWaypoints()).singleElement()
                .extracting(UserRouteWaypoint::getPhotoUrl)
                .isNull();
        verifyNoInteractions(storageService);
    }

    @Test
    void uploadsNonEmptyPhotoAndStoresReturnedUrl() {
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "place.jpg", "image/jpeg", new byte[]{1});
        String storedUrl = "/uploads/user-routes/place.jpg";
        when(storageService.store(photo)).thenReturn(storedUrl);

        userRouteService.addWaypoint(
                ROUTE_ID, AUTHOR_EMAIL, "이미지 있는 장소", "메모", 37.3, 127.3, photo);

        assertThat(route.getWaypoints()).singleElement()
                .extracting(UserRouteWaypoint::getPhotoUrl)
                .isEqualTo(storedUrl);
        verify(storageService).store(photo);
    }
}
