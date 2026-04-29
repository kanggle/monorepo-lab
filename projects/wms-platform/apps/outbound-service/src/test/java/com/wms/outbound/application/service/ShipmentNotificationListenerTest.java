package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.application.service.fakes.FakeShipmentPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.domain.model.Shipment;
import com.wms.outbound.domain.model.TmsStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit-level coverage of the post-commit TMS notification path (AC-03 of
 * TASK-BE-040).
 *
 * <p>The listener itself does not need a Spring TX context because the
 * TX-bound work was extracted into {@link ShipmentNotificationPersistence}
 * (the original self-invocation bug). These tests exercise the
 * {@link ShipmentNotificationPersistence} bean directly and the
 * {@link ShipmentNotificationListener} dispatch path, asserting that:
 *
 * <ol>
 *   <li>a successful TMS ack drives the shipment to {@code NOTIFIED}; and</li>
 *   <li>a TMS failure drives the shipment to {@code NOTIFY_FAILED} and the
 *       saga to {@code SHIPPED_NOT_NOTIFIED}.</li>
 * </ol>
 *
 * <p>No Mockito / Testcontainers / Spring context — the proxy-bypass concern
 * was the original bug; verifying the unit behaviour through plain port fakes
 * is exactly the right contract here (the AOP wiring is integration-test
 * territory).
 */
class ShipmentNotificationListenerTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(T0, ZoneOffset.UTC);

    private FakeShipmentPersistencePort shipmentPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOrderPersistencePort orderPersistence;
    private OutboundSagaCoordinator coordinator;
    private ShipmentNotificationPersistence persistence;

    private UUID shipmentId;
    private UUID orderId;
    private UUID sagaId;

    @BeforeEach
    void setUp() {
        shipmentPersistence = new FakeShipmentPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        orderPersistence = new FakeOrderPersistencePort();
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence, fixedClock);
        persistence = new ShipmentNotificationPersistence(shipmentPersistence, coordinator);

        shipmentId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        sagaId = UUID.randomUUID();
    }

    @Test
    void tmsSuccess_marksShipmentNotified() {
        seedShipment(TmsStatus.PENDING);
        seedSaga(SagaStatus.SHIPPED);

        ShipmentNotificationPort tms = id -> new TmsAcknowledgement(true, UUID.randomUUID().toString());
        ShipmentNotificationListener listener = new ShipmentNotificationListener(
                tms, persistence, fixedClock);

        listener.onShipmentNotifyTrigger(new ShipmentNotifyTrigger(sagaId, shipmentId));

        Shipment after = shipmentPersistence.findById(shipmentId).orElseThrow();
        assertThat(after.getTmsStatus()).isEqualTo(TmsStatus.NOTIFIED);
        assertThat(after.getTmsNotifiedAt()).isEqualTo(T0);
        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.SHIPPED);
    }

    @Test
    void tmsFailure_marksShipmentNotifyFailed_advancesSagaToShippedNotNotified() {
        seedShipment(TmsStatus.PENDING);
        seedSaga(SagaStatus.SHIPPED);

        ShipmentNotificationPort tms = id -> {
            throw new RuntimeException("circuit-open");
        };
        ShipmentNotificationListener listener = new ShipmentNotificationListener(
                tms, persistence, fixedClock);

        listener.onShipmentNotifyTrigger(new ShipmentNotifyTrigger(sagaId, shipmentId));

        Shipment after = shipmentPersistence.findById(shipmentId).orElseThrow();
        assertThat(after.getTmsStatus()).isEqualTo(TmsStatus.NOTIFY_FAILED);
        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.SHIPPED_NOT_NOTIFIED);
    }

    @Test
    void persistenceMarkNotified_directly_transitionsTmsStatus() {
        // Direct exercise of the @Transactional helper — the Spring proxy
        // is the production safety net but the underlying logic is
        // proxy-agnostic and must succeed when called as a plain method.
        seedShipment(TmsStatus.PENDING);

        UUID requestId = UUID.randomUUID();
        persistence.markNotified(shipmentId, T0, requestId);

        Shipment after = shipmentPersistence.findById(shipmentId).orElseThrow();
        assertThat(after.getTmsStatus()).isEqualTo(TmsStatus.NOTIFIED);
        assertThat(after.getTmsRequestId()).isEqualTo(requestId);
    }

    @Test
    void persistenceMarkFailed_directly_advancesShipmentAndSaga() {
        seedShipment(TmsStatus.PENDING);
        seedSaga(SagaStatus.SHIPPED);

        persistence.markFailed(sagaId, shipmentId, T0, "vendor-timeout");

        Shipment after = shipmentPersistence.findById(shipmentId).orElseThrow();
        assertThat(after.getTmsStatus()).isEqualTo(TmsStatus.NOTIFY_FAILED);
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElseThrow();
        assertThat(saga.status()).isEqualTo(SagaStatus.SHIPPED_NOT_NOTIFIED);
        assertThat(saga.failureReason()).isEqualTo("vendor-timeout");
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private void seedShipment(TmsStatus tmsStatus) {
        Shipment shipment = new Shipment(
                shipmentId,
                orderId,
                "SHP-20260429-0001",
                "FEDEX",
                null,
                T0,
                tmsStatus,
                null,
                null,
                0L,
                T0,
                "creator",
                T0);
        shipmentPersistence.save(shipment);
    }

    private void seedSaga(SagaStatus status) {
        OutboundSaga saga = new OutboundSaga(
                sagaId, orderId, status, sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);
    }
}
