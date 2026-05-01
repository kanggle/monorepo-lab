package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.ConfirmShippingCommand;
import com.wms.outbound.application.result.ShipmentResult;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.application.service.fakes.FakeApplicationEventPublisher;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakePackingPersistencePort;
import com.wms.outbound.application.service.fakes.FakePickingConfirmationPersistencePort;
import com.wms.outbound.application.service.fakes.FakePickingPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.application.service.fakes.FakeShipmentPersistencePort;
import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.PackingType;
import com.wms.outbound.domain.model.PackingUnit;
import com.wms.outbound.domain.model.PackingUnitLine;
import com.wms.outbound.domain.model.PackingUnitStatus;
import com.wms.outbound.domain.model.PickingConfirmation;
import com.wms.outbound.domain.model.PickingConfirmationLine;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.PickingRequestLine;
import com.wms.outbound.domain.model.PickingRequestStatus;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.domain.model.TmsStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ConfirmShippingServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(T0, ZoneOffset.UTC);

    private FakeOrderPersistencePort orderPersistence;
    private FakePickingPersistencePort pickingPersistence;
    private FakePickingConfirmationPersistencePort pickingConfirmationPersistence;
    private FakePackingPersistencePort packingPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeShipmentPersistencePort shipmentPersistence;
    private FakeOutboxWriterPort outboxWriter;
    private FakeApplicationEventPublisher eventPublisher;
    private OutboundSagaCoordinator coordinator;
    private ConfirmShippingService service;

    private UUID orderId;
    private UUID skuId;
    private UUID warehouseId;
    private UUID partnerId;
    private UUID locationId;
    private UUID orderLineId;
    private UUID pickingRequestId;
    private UUID pickingConfirmationId;
    private UUID sagaId;

    @BeforeEach
    void setUp() {
        orderPersistence = new FakeOrderPersistencePort();
        pickingPersistence = new FakePickingPersistencePort();
        pickingConfirmationPersistence = new FakePickingConfirmationPersistencePort();
        packingPersistence = new FakePackingPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        shipmentPersistence = new FakeShipmentPersistencePort();
        outboxWriter = new FakeOutboxWriterPort();
        eventPublisher = new FakeApplicationEventPublisher();
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence, fixedClock);
        service = new ConfirmShippingService(orderPersistence, pickingPersistence,
                pickingConfirmationPersistence, packingPersistence, sagaPersistence,
                shipmentPersistence, coordinator, outboxWriter, eventPublisher, fixedClock);

        orderId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        partnerId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        orderLineId = UUID.randomUUID();
        pickingRequestId = UUID.randomUUID();
        pickingConfirmationId = UUID.randomUUID();
        sagaId = UUID.randomUUID();
    }

    @Test
    void happyPath_packedOrderAdvancesToShippedAndShipmentCreated() {
        seedOrder(OrderStatus.PACKED);
        seedPickingArtifacts();
        seedPackedUnit();
        seedSaga(SagaStatus.PACKING_CONFIRMED);

        ConfirmShippingCommand cmd = new ConfirmShippingCommand(
                orderId, 0L, "CJ", "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        ShipmentResult result = service.confirm(cmd);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.SHIPPED.name());
        assertThat(result.sagaState()).isEqualTo(SagaStatus.SHIPPED.name());
        assertThat(result.tmsStatus()).isEqualTo(TmsStatus.PENDING.name());
        // shipment_no format aligned to outbound-service-api.md §4.1 example
        // (SHP-YYYYMMDD-NNNN) per TASK-BE-040 AC-06.
        assertThat(result.shipmentNo()).startsWith("SHP-");
        assertThat(shipmentPersistence.count()).isEqualTo(1);
        assertThat(outboxWriter.countByType("outbound.shipping.confirmed")).isEqualTo(1);
        assertThat(eventPublisher.published).hasSize(1);
        assertThat(eventPublisher.published.get(0))
                .isInstanceOf(ShipmentNotifyTrigger.class);
    }

    @Test
    void orderNotPacked_raisesStateTransitionInvalid() {
        seedOrder(OrderStatus.PICKING); // wrong state
        seedPickingArtifacts();
        seedPackedUnit();
        seedSaga(SagaStatus.RESERVED);

        ConfirmShippingCommand cmd = new ConfirmShippingCommand(
                orderId, 0L, "CJ", "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.confirm(cmd))
                .isInstanceOf(StateTransitionInvalidException.class);
        assertThat(shipmentPersistence.count()).isZero();
        assertThat(outboxWriter.published).isEmpty();
        assertThat(eventPublisher.published).isEmpty();
    }

    @Test
    void staleVersion_raisesOptimisticLockingFailure() {
        seedOrder(OrderStatus.PACKED); // version=0
        seedPickingArtifacts();
        seedPackedUnit();
        seedSaga(SagaStatus.PACKING_CONFIRMED);

        ConfirmShippingCommand stale = new ConfirmShippingCommand(
                orderId, 99L /* != actual 0 */, "CJ", "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.confirm(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(shipmentPersistence.count()).isZero();
        assertThat(outboxWriter.published).isEmpty();
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private void seedOrder(OrderStatus status) {
        OrderLine line = new OrderLine(orderLineId, orderId, 1, skuId, null, 50);
        Order order = new Order(orderId, "ORD-1", OrderSource.MANUAL,
                partnerId, warehouseId, null, null, status,
                0L, T0, "creator", T0, "creator", List.of(line));
        orderPersistence.save(order);
    }

    private void seedPickingArtifacts() {
        PickingRequestLine prl = new PickingRequestLine(
                UUID.randomUUID(), pickingRequestId, orderLineId, skuId, null, locationId, 50);
        PickingRequest pr = new PickingRequest(
                pickingRequestId, orderId, sagaId, warehouseId,
                PickingRequestStatus.SUBMITTED, 0L, T0, T0, List.of(prl));
        pickingPersistence.save(pr);

        PickingConfirmationLine pcl = new PickingConfirmationLine(
                UUID.randomUUID(), pickingConfirmationId, orderLineId, skuId, null,
                locationId, 50);
        PickingConfirmation pc = new PickingConfirmation(
                pickingConfirmationId, pickingRequestId, orderId,
                "user-1", T0, null, List.of(pcl));
        pickingConfirmationPersistence.save(pc);
    }

    private void seedPackedUnit() {
        UUID unitId = UUID.randomUUID();
        PackingUnitLine pul = new PackingUnitLine(
                UUID.randomUUID(), unitId, orderLineId, skuId, null, 50);
        PackingUnit unit = new PackingUnit(
                unitId, orderId, "BOX-001", PackingType.BOX,
                null, null, null, null, null, PackingUnitStatus.SEALED,
                0L, T0, T0, List.of(pul));
        packingPersistence.save(unit);
    }

    private void seedSaga(SagaStatus status) {
        OutboundSaga saga = new OutboundSaga(
                sagaId, orderId, status, pickingRequestId, null, T0, T0, 0L);
        sagaPersistence.save(saga);
    }
}
