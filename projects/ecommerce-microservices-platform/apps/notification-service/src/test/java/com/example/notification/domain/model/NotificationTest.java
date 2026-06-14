package com.example.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification 도메인 모델 단위 테스트")
class NotificationTest {

    @Test
    @DisplayName("알림 생성 시 PENDING 상태로 초기화된다")
    void create_initialStatusIsPending() {
        Notification notification = Notification.create(
                "ecommerce", "user-1", NotificationChannel.EMAIL, "Subject", "Body", "event-1");

        assertThat(notification.getNotificationId()).isNotNull();
        assertThat(notification.getTenantId()).isEqualTo("ecommerce");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRetryCount()).isZero();
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("create에 빈 tenantId를 주면 기본 테넌트(ecommerce)로 귀결된다")
    void create_blankTenant_defaultsToEcommerce() {
        Notification notification = Notification.create(
                "", "user-1", NotificationChannel.EMAIL, "Subject", "Body", "event-1");

        assertThat(notification.getTenantId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("markSent 호출 시 SENT 상태로 변경되고 sentAt이 설정된다")
    void markSent_changesStatusToSent() {
        Notification notification = Notification.create(
                "ecommerce", "user-1", NotificationChannel.EMAIL, "Subject", "Body", "event-1");

        notification.markSent();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed 호출 시 FAILED 상태로 변경되고 retryCount가 증가한다")
    void markFailed_changesStatusToFailed() {
        Notification notification = Notification.create(
                "ecommerce", "user-1", NotificationChannel.EMAIL, "Subject", "Body", "event-1");

        notification.markFailed();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("FAILED 상태에서 maxRetries 미만이면 재시도 가능하다")
    void canRetry_failedAndBelowMax_returnsTrue() {
        Notification notification = Notification.create(
                "ecommerce", "user-1", NotificationChannel.EMAIL, "Subject", "Body", "event-1");

        notification.markFailed();

        assertThat(notification.canRetry(3)).isTrue();
    }

    @Test
    @DisplayName("retryCount가 maxRetries 이상이면 재시도 불가하다")
    void canRetry_reachedMax_returnsFalse() {
        Notification notification = Notification.create(
                "ecommerce", "user-1", NotificationChannel.EMAIL, "Subject", "Body", "event-1");

        notification.markFailed();
        notification.markFailed();
        notification.markFailed();

        assertThat(notification.canRetry(3)).isFalse();
    }

    @Test
    @DisplayName("reconstitute로 도메인 객체를 복원할 수 있다")
    void reconstitute_restoresNotification() {
        Notification notification = Notification.reconstitute(
                "noti-1", "ecommerce", "user-1", NotificationChannel.EMAIL,
                "Subject", "Body", NotificationStatus.SENT,
                "event-1", 0, null, null);

        assertThat(notification.getNotificationId()).isEqualTo("noti-1");
        assertThat(notification.getTenantId()).isEqualTo("ecommerce");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    }
}
