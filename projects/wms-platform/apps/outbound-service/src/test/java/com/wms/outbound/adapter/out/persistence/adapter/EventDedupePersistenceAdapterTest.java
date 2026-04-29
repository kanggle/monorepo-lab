package com.wms.outbound.adapter.out.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wms.outbound.adapter.out.persistence.entity.OutboundEventDedupe;
import com.wms.outbound.adapter.out.persistence.repository.OutboundEventDedupeRepository;
import com.wms.outbound.application.port.out.EventDedupePort;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class EventDedupePersistenceAdapterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-28T10:00:00Z");

    private OutboundEventDedupeRepository repository;
    private EntityManager entityManager;
    private EventDedupePersistenceAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(OutboundEventDedupeRepository.class);
        entityManager = mock(EntityManager.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        adapter = new EventDedupePersistenceAdapter(repository, clock);

        Field field = EventDedupePersistenceAdapter.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(adapter, entityManager);
    }

    @Test
    void firstOccurrenceRunsWorkAndReturnsApplied() {
        AtomicInteger counter = new AtomicInteger();
        EventDedupePort.Outcome outcome = adapter.process(
                UUID.randomUUID(),
                "master.location.created",
                counter::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(counter.get()).isEqualTo(1);
        verify(repository, times(1)).save(any(OutboundEventDedupe.class));
        verify(entityManager, times(1)).flush();
    }

    @Test
    void duplicateOccurrenceSkipsWorkAndReturnsIgnored() {
        doThrow(new DataIntegrityViolationException("duplicate event_id"))
                .when(entityManager).flush();
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
        verify(repository, never()).save(any());
    }

    @Test
    void workExceptionPropagatesAfterDedupeRowWritten() {
        doNothing().when(entityManager).flush();
        RuntimeException boom = new RuntimeException("downstream failure");

        assertThatThrownBy(() -> adapter.process(
                UUID.randomUUID(),
                "master.location.created",
                () -> { throw boom; }))
                .isSameAs(boom);
        verify(repository, times(1)).save(any(OutboundEventDedupe.class));
    }
}
