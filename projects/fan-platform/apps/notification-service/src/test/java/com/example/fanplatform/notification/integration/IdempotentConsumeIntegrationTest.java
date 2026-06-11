package com.example.fanplatform.notification.integration;

import com.example.fanplatform.notification.infrastructure.jpa.NotificationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Re-delivering the same {@code eventId} (at-least-once) creates NO duplicate
 * notification — the {@code processed_events} guard + unique {@code source_event_id}
 * make the consume idempotent (architecture.md § Idempotency, AC-2).
 */
class IdempotentConsumeIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    private NotificationJpaRepository notifications;

    @BeforeEach
    void setUp() {
        truncateAll();
        awaitListenersAssigned();
    }

    @Test
    @DisplayName("duplicate eventId → exactly one notification row")
    void duplicateDeliveryProducesSingleRow() {
        String envelope = activatedEnvelope("evt-dup-1", "mem-1", "acc-1", "PREMIUM");

        // Same eventId twice (at-least-once redelivery).
        producer().send(TOPIC_ACTIVATED, "mem-1", envelope);
        producer().send(TOPIC_ACTIVATED, "mem-1", envelope);

        // Wait until at least one row exists, then assert it never exceeds one.
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(notifications.count()).isEqualTo(1));

        // Give the second delivery time to be (idempotently) consumed.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(notifications.count()).isEqualTo(1));
    }
}
