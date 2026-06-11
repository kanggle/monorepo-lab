package com.example.fanplatform.notification.domain.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTypeTest {

    @Test
    void mapsActivatedToWelcome() {
        assertThat(NotificationType.fromEventType("fan.membership.activated"))
                .isEqualTo(NotificationType.WELCOME);
    }

    @Test
    void mapsCanceledToCancellation() {
        assertThat(NotificationType.fromEventType("fan.membership.canceled"))
                .isEqualTo(NotificationType.CANCELLATION);
    }

    @Test
    void mapsExpiredToExpiryReminder() {
        // TASK-FAN-BE-014: the producer's expiry sweeper now emits expired.v1.
        assertThat(NotificationType.fromEventType("fan.membership.expired"))
                .isEqualTo(NotificationType.EXPIRY_REMINDER);
    }

    @Test
    void rejectsUnknownTypes() {
        assertThatThrownBy(() -> NotificationType.fromEventType("something.else"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
