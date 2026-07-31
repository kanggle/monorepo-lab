package com.example.fanplatform.notification.infrastructure.messaging.idempotency;

import com.example.messaging.dedupe.EventDedupePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventDedupePortJpaAdapter}'s INSERT-or-skip control flow
 * (TASK-FAN-BE-042). The native {@code insertIfAbsent} affected-row count is
 * mocked here; the authoritative real-Postgres coverage (unique-constraint
 * collision, cross-table transactional atomicity, a genuine concurrent race) is
 * {@code EventDedupePortJpaAdapterIntegrationTest}.
 */
class EventDedupePortJpaAdapterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-30T10:00:00Z");

    private ProcessedEventJpaRepository repository;
    private EventDedupePortJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(ProcessedEventJpaRepository.class);
        adapter = new EventDedupePortJpaAdapter(repository, () -> FIXED_NOW);
    }

    @Test
    @DisplayName("first occurrence (insertIfAbsent → 1) runs work and returns APPLIED")
    void firstOccurrenceRunsWorkAndReturnsApplied() {
        when(repository.insertIfAbsent(any(), any(), any())).thenReturn(1);
        AtomicInteger counter = new AtomicInteger();
        UUID eventId = UUID.randomUUID();

        EventDedupePort.Outcome outcome = adapter.process(
                eventId, "fan.membership.activated", counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(counter.get()).isEqualTo(1);
        verify(repository, times(1)).insertIfAbsent(
                eq(eventId.toString()), eq("fan.membership.activated"), eq(FIXED_NOW));
    }

    @Test
    @DisplayName("duplicate occurrence (insertIfAbsent → 0) skips work and returns IGNORED_DUPLICATE "
            + "— guard: this is the assertion a hardcoded-APPLIED mutation would fail")
    void duplicateOccurrenceSkipsWorkAndReturnsIgnored() {
        when(repository.insertIfAbsent(any(), any(), any())).thenReturn(0);
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(
                UUID.randomUUID(), "fan.membership.activated", counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("rejects a null eventId without touching the repository")
    void rejectsNullEventId() {
        assertThatThrownBy(() -> adapter.process(null, "fan.membership.activated", () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    @DisplayName("work's exception propagates after the dedupe row insert (caller's transaction rolls both back)")
    void workExceptionPropagatesAfterDedupeRowWritten() {
        when(repository.insertIfAbsent(any(), any(), any())).thenReturn(1);
        RuntimeException boom = new RuntimeException("downstream failure");

        assertThatThrownBy(() -> adapter.process(
                UUID.randomUUID(), "fan.membership.activated", () -> {
                    throw boom;
                }))
                .isSameAs(boom);
        verify(repository, times(1)).insertIfAbsent(any(), any(), any());
    }
}
