package com.example.notification.application.service;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.result.GetNotificationResult;
import com.example.notification.application.result.ListNotificationsResult;
import com.example.notification.domain.exception.NotificationNotFoundException;
import com.example.notification.domain.exception.UnauthorizedNotificationAccessException;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationQueryService 단위 테스트")
class NotificationQueryServiceTest {

    @InjectMocks
    private NotificationQueryService notificationQueryService;

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("사용자별 알림 목록을 조회하면 result DTO를 반환한다")
    void getNotifications_returnsResultDto() {
        Notification notification = Notification.reconstitute(
                "noti-1", "user-1", NotificationChannel.EMAIL,
                "Subject", "Body", NotificationStatus.SENT,
                "event-1", 0, null, null);
        PageQuery pageQuery = new PageQuery(0, 20, null, null);
        PageResult<Notification> pageResult = new PageResult<>(List.of(notification), 0, 20, 1L, 1);
        given(notificationRepository.findByUserId("user-1", pageQuery))
                .willReturn(pageResult);

        PageResult<ListNotificationsResult.NotificationSummary> result =
                notificationQueryService.getNotifications("user-1", pageQuery);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.content().get(0).notificationId()).isEqualTo("noti-1");
        assertThat(result.content().get(0).channel()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("알림 상세 조회 시 GetNotificationResult를 반환한다")
    void getNotificationDetail_returnsResultDto() {
        Notification notification = Notification.reconstitute(
                "noti-1", "user-1", NotificationChannel.EMAIL,
                "Subject", "Body", NotificationStatus.SENT,
                "event-1", 0, null, null);
        given(notificationRepository.findById("noti-1")).willReturn(Optional.of(notification));

        GetNotificationResult result = notificationQueryService.getNotificationDetail("user-1", "noti-1");

        assertThat(result.notificationId()).isEqualTo("noti-1");
        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.channel()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("알림 상세 조회 시 존재하지 않으면 예외가 발생한다")
    void getNotificationDetail_notFound_throws() {
        given(notificationRepository.findById("noti-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationQueryService.getNotificationDetail("user-1", "noti-1"))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자의 알림을 조회하면 권한 예외가 발생한다")
    void getNotificationDetail_wrongUser_throws() {
        Notification notification = Notification.reconstitute(
                "noti-1", "user-2", NotificationChannel.EMAIL,
                "Subject", "Body", NotificationStatus.SENT,
                "event-1", 0, null, null);
        given(notificationRepository.findById("noti-1")).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationQueryService.getNotificationDetail("user-1", "noti-1"))
                .isInstanceOf(UnauthorizedNotificationAccessException.class);
    }
}
