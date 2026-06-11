package com.example.fanplatform.notification.integration;

import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.example.fanplatform.notification.infrastructure.jpa.NotificationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Publish {@code fan.membership.activated.v1} → a WELCOME notification row is
 * created; publish {@code canceled.v1} → a CANCELLATION row (architecture.md §
 * Event → Notification mapping, AC-1).
 */
class MembershipEventConsumeIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    private NotificationJpaRepository notifications;

    @BeforeEach
    void setUp() {
        truncateAll();
        awaitListenersAssigned();
    }

    @Test
    @DisplayName("activated.v1 → WELCOME notification persisted (UNREAD, account-scoped)")
    void activatedCreatesWelcome() {
        producer().send(TOPIC_ACTIVATED, "mem-1",
                activatedEnvelope("evt-act-1", "mem-1", "acc-1", "PREMIUM"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var all = notifications.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getType()).isEqualTo(NotificationType.WELCOME);
            assertThat(all.get(0).getStatus()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(all.get(0).getAccountId()).isEqualTo("acc-1");
            assertThat(all.get(0).getMembershipId()).isEqualTo("mem-1");
            assertThat(all.get(0).getSourceEventId()).isEqualTo("evt-act-1");
            assertThat(all.get(0).getTitle()).isEqualTo("Welcome to PREMIUM membership");
        });
    }

    @Test
    @DisplayName("canceled.v1 → CANCELLATION notification persisted")
    void canceledCreatesCancellation() {
        producer().send(TOPIC_CANCELED, "mem-2",
                canceledEnvelope("evt-can-1", "mem-2", "acc-2", "MEMBERS_ONLY"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var all = notifications.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getType()).isEqualTo(NotificationType.CANCELLATION);
            assertThat(all.get(0).getAccountId()).isEqualTo("acc-2");
            assertThat(all.get(0).getTitle()).isEqualTo("Your MEMBERS_ONLY membership was canceled");
        });
    }
}
