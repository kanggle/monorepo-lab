package com.example.product.infrastructure.reconciliation;

import com.example.messaging.dedupe.EventDedupePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WmsReconciliationDedupe} as an {@link EventDedupePort} adapter
 * (ADR-MONO-058 D7, TASK-BE-569). Mirrors {@code ReservationEventDedupeTest} — same
 * contract, separate service-owned table.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WmsReconciliationDedupe (EventDedupePort 어댑터) 단위 테스트")
class WmsReconciliationDedupeTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-30T10:00:00Z");

    private WmsProcessedEventJpaRepository repository;
    private WmsReconciliationDedupe adapter;

    @BeforeEach
    void setUp() {
        repository = mock(WmsProcessedEventJpaRepository.class);
        adapter = new WmsReconciliationDedupe(repository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("첫 수신 eventId는 처리 기록을 남기고 work를 실행, APPLIED 반환")
    void firstOccurrence_runsWorkAndReturnsApplied() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsById(eventId)).thenReturn(false);
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(eventId, "wms.master.sku", counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(counter.get()).isEqualTo(1);
        verify(repository).save(any(WmsProcessedEventEntity.class));
    }

    @Test
    @DisplayName("중복 eventId는 work를 재실행하지 않고 IGNORED_DUPLICATE 반환")
    void duplicateEventId_skipsWorkAndReturnsIgnored() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsById(eventId)).thenReturn(true);
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(eventId, "wms.master.sku", counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(counter.get()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("null eventId는 dedupe를 건너뛰고 work를 실행한다 (기존 permissive fallback 유지)")
    void nullEventId_skipsDedupeButRunsWork() {
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(null, "wms.master.sku", counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(counter.get()).isEqualTo(1);
        verify(repository, never()).existsById(any());
        verify(repository, never()).save(any());
    }
}
