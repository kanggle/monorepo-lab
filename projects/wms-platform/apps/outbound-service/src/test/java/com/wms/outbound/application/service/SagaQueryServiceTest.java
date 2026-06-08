package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.result.SagaResult;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SagaQueryService} (outbound-service-api.md §5.1).
 *
 * <p>Verifies that {@code findByOrderId} maps all {@link OutboundSaga} fields
 * — {@code sagaId}, {@code orderId}, {@code state} (= {@code SagaStatus.name()}),
 * {@code failureReason}, {@code startedAt}, {@code lastTransitionAt},
 * {@code version} — to the {@link SagaResult} record correctly, and that the
 * absent case returns an empty Optional.
 */
class SagaQueryServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-04-29T10:05:00Z");

    private FakeSagaPersistencePort fakeSaga;
    private SagaQueryService service;

    @BeforeEach
    void setUp() {
        fakeSaga = new FakeSagaPersistencePort();
        service = new SagaQueryService(fakeSaga);
    }

    @Test
    @DisplayName("findByOrderId maps all OutboundSaga fields to SagaResult (state = status.name())")
    void findByOrderId_mapsAllFields() {
        UUID sagaId  = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        OutboundSaga saga = new OutboundSaga(
                sagaId, orderId,
                SagaStatus.RESERVED,
                UUID.randomUUID(),   // pickingRequestId
                null,                // failureReason
                T0,                  // startedAt
                T1,                  // lastTransitionAt
                3L);                 // version
        fakeSaga.save(saga);

        Optional<SagaResult> result = service.findByOrderId(orderId);

        assertThat(result).isPresent();
        SagaResult r = result.get();
        assertThat(r.sagaId()).isEqualTo(sagaId);
        assertThat(r.orderId()).isEqualTo(orderId);
        assertThat(r.state()).isEqualTo("RESERVED");
        assertThat(r.failureReason()).isNull();
        assertThat(r.startedAt()).isEqualTo(T0);
        assertThat(r.lastTransitionAt()).isEqualTo(T1);
        assertThat(r.version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("findByOrderId — failureReason is mapped when present")
    void findByOrderId_failureReasonMapped() {
        UUID orderId = UUID.randomUUID();

        OutboundSaga saga = new OutboundSaga(
                UUID.randomUUID(), orderId,
                SagaStatus.RESERVE_FAILED,
                null,
                "INSUFFICIENT_STOCK",
                T0, T0, 1L);
        fakeSaga.save(saga);

        SagaResult r = service.findByOrderId(orderId).orElseThrow();

        assertThat(r.state()).isEqualTo("RESERVE_FAILED");
        assertThat(r.failureReason()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("findByOrderId — state equals SagaStatus.name() for every terminal state")
    void findByOrderId_stateIsStatusName_completed() {
        UUID orderId = UUID.randomUUID();

        OutboundSaga saga = new OutboundSaga(
                UUID.randomUUID(), orderId,
                SagaStatus.COMPLETED,
                UUID.randomUUID(), null, T0, T0, 5L);
        fakeSaga.save(saga);

        SagaResult r = service.findByOrderId(orderId).orElseThrow();
        assertThat(r.state()).isEqualTo(SagaStatus.COMPLETED.name());
    }

    @Test
    @DisplayName("findByOrderId — no saga for the order → empty Optional")
    void findByOrderId_noSaga_returnsEmpty() {
        Optional<SagaResult> result = service.findByOrderId(UUID.randomUUID());
        assertThat(result).isEmpty();
    }
}
