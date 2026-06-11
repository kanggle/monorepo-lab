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
    void rejectsExpiredAndUnknownTypes() {
        // expired is forward-declared but NOT emitted/consumed — never mapped.
        assertThatThrownBy(() -> NotificationType.fromEventType("fan.membership.expired"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationType.fromEventType("something.else"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
