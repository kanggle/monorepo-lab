package com.example.settlement.infrastructure.persistence;

import com.example.messaging.dedupe.EventDedupePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SettlementEventDedupe} as an {@link EventDedupePort} adapter
 * (ADR-MONO-058 D7, TASK-BE-569) — the replacement for the removed service-local
 * {@code ProcessedEventStore}/{@code ProcessedEventStoreImpl}. Pins the {@code process(...)}
 * contract over the locally owned {@code String}-keyed {@code processed_event} table.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementEventDedupe (EventDedupePort 어댑터) 단위 테스트")
class SettlementEventDedupeTest {

    private ProcessedEventJpaRepository repository;
    private SettlementEventDedupe adapter;

    @BeforeEach
    void setUp() {
        repository = mock(ProcessedEventJpaRepository.class);
        adapter = new SettlementEventDedupe(repository);
    }

    @Test
    @DisplayName("새로운 eventId는 work를 실행하고 APPLIED를 반환한다")
    void process_newEventId_runsWorkAndReturnsApplied() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId.toString())).thenReturn(false);
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(eventId, "PaymentCompleted", counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(counter.get()).isEqualTo(1);
        verify(repository).save(any(ProcessedEventJpaEntity.class));
    }

    @Test
    @DisplayName("이미 처리된 eventId는 work를 재실행하지 않고 IGNORED_DUPLICATE를 반환한다")
    void process_existingEventId_skipsWorkAndReturnsIgnored() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId.toString())).thenReturn(true);
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(eventId, "PaymentCompleted", counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(counter.get()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("eventId가 null이면 중복 체크를 건너뛰고 work를 실행한다 (기존 blank-id fallback 유지)")
    void process_nullEventId_skipsDedupeButRunsWork() {
        AtomicInteger counter = new AtomicInteger();

        EventDedupePort.Outcome outcome = adapter.process(null, "PaymentCompleted", counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(counter.get()).isEqualTo(1);
        verify(repository, never()).existsByEventId(any());
        verify(repository, never()).save(any());
    }
}
