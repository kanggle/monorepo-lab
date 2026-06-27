package com.example.platform.notification.consumer;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.messaging.envelope.EventEnvelope;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationConsumerSupportTest {

    /**
     * Fake dedupe port: runs the work on the first eventId it sees, ignores
     * subsequent occurrences (the EventDedupePort APPLIED / IGNORED_DUPLICATE
     * semantics) — no DB needed.
     */
    private static final class FakeDedupe implements EventDedupePort {
        private final java.util.Set<UUID> seen = new java.util.HashSet<>();
        @Override public Outcome process(UUID eventId, String eventType, Runnable work) {
            if (!seen.add(eventId)) {
                return Outcome.IGNORED_DUPLICATE;
            }
            work.run();
            return Outcome.APPLIED;
        }
    }

    private EventEnvelope envelope(UUID id) {
        return new EventEnvelope(
                id, "erp.approval.submitted", 1, Instant.now(),
                "approval-service", "ApprovalRequest", "PR-1",
                null, null, JsonNodeFactory.instance.objectNode());
    }

    @Test
    void firstOccurrence_runsWork_andReturnsApplied() {
        NotificationConsumerSupport support = new NotificationConsumerSupport(new FakeDedupe());
        AtomicInteger runs = new AtomicInteger();
        EventEnvelope env = envelope(UUID.randomUUID());

        EventDedupePort.Outcome outcome = support.handle(env, e -> runs.incrementAndGet());

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void duplicate_skipsWork_andReturnsIgnoredDuplicate() {
        NotificationConsumerSupport support = new NotificationConsumerSupport(new FakeDedupe());
        AtomicInteger runs = new AtomicInteger();
        UUID id = UUID.randomUUID();

        support.handle(envelope(id), e -> runs.incrementAndGet());
        EventDedupePort.Outcome second = support.handle(envelope(id), e -> runs.incrementAndGet());

        assertThat(second).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(runs.get()).isEqualTo(1); // work ran only once
    }

    @Test
    void missingEventId_throws() {
        NotificationConsumerSupport support = new NotificationConsumerSupport(new FakeDedupe());
        EventEnvelope env = new EventEnvelope(
                null, "erp.approval.submitted", 1, Instant.now(),
                "approval-service", "ApprovalRequest", "PR-1", null, null,
                JsonNodeFactory.instance.objectNode());

        assertThatThrownBy(() -> support.handle(env, e -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void blankEventType_throws() {
        NotificationConsumerSupport support = new NotificationConsumerSupport(new FakeDedupe());
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(), "  ", 1, Instant.now(),
                "approval-service", "ApprovalRequest", "PR-1", null, null,
                JsonNodeFactory.instance.objectNode());

        assertThatThrownBy(() -> support.handle(env, e -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }
}
