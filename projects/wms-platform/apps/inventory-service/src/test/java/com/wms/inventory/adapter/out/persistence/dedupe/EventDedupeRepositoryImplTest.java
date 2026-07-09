package com.wms.inventory.adapter.out.persistence.dedupe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.inventory.application.port.out.EventDedupePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the dedupe adapter's INSERT-or-skip control flow. The native
 * {@code insertIfAbsent} affected-row count is mocked; the authoritative
 * merge-vs-insert behaviour against a real database is covered by
 * {@code PutawayCompletedConsumerIntegrationTest.redeliveryIsDeduped} (TASK-BE-488).
 */
class EventDedupeRepositoryImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-25T10:00:00Z");

    private EventDedupeJpaRepository repository;
    private EventDedupeRepositoryImpl adapter;

    @BeforeEach
    void setUp() {
        repository = mock(EventDedupeJpaRepository.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        adapter = new EventDedupeRepositoryImpl(repository, clock);
    }

    @Test
    void firstOccurrenceRunsWorkAndReturnsApplied() {
        when(repository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(
                UUID.randomUUID(),
                "master.location.created",
                counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(counter.get()).isEqualTo(1);
        verify(repository, times(1)).insertIfAbsent(
                any(), eq("master.location.created"), eq(FIXED_NOW), eq("APPLIED"));
    }

    @Test
    void duplicateOccurrenceSkipsWorkAndReturnsIgnored() {
        when(repository.insertIfAbsent(any(), any(), any(), any())).thenReturn(0);
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(
                UUID.randomUUID(),
                "master.location.created",
                counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(counter.get()).isZero();
    }

    @Test
    void rejectsNullEventId() {
        assertThatThrownBy(() -> adapter.process(null, "type", () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insertIfAbsent(any(), any(), any(), any());
    }

    @Test
    void workExceptionPropagatesAfterDedupeRowWritten() {
        when(repository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);
        RuntimeException boom = new RuntimeException("downstream failure");

        assertThatThrownBy(() -> adapter.process(
                UUID.randomUUID(),
                "master.location.created",
                () -> { throw boom; }))
                .isSameAs(boom);
        verify(repository, times(1)).insertIfAbsent(any(), any(), any(), any());
    }
}
