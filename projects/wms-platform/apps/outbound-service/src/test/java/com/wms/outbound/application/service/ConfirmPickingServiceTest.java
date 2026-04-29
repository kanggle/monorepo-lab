package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.ConfirmPickingCommand;
import com.wms.outbound.application.command.ConfirmPickingLineCommand;
import com.wms.outbound.application.result.PickingConfirmationResult;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.application.service.fakes.FakeMasterReadModelPort;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakePickingConfirmationPersistencePort;
import com.wms.outbound.application.service.fakes.FakePickingPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.exception.LotRequiredException;
import com.wms.outbound.domain.exception.PickingIncompleteException;
import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.PickingRequestLine;
import com.wms.outbound.domain.model.PickingRequestStatus;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmPickingServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(T0, ZoneOffset.UTC);

    private FakeOrderPersistencePort orderPersistence;
    private FakePickingPersistencePort pickingPersistence;
    private FakePickingConfirmationPersistencePort pickingConfirmationPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOutboxWriterPort outboxWriter;
    private FakeMasterReadModelPort masterReadModel;
    private OutboundSagaCoordinator coordinator;
    private ConfirmPickingService service;

    private UUID orderId;
    private UUID skuId;
    private UUID warehouseId;
    private UUID partnerId;
    private UUID locationId;
    private UUID orderLineId;
    private UUID pickingRequestId;
    private UUID sagaId;

    @BeforeEach
    void setUp() {
        orderPersistence = new FakeOrderPersistencePort();
        pickingPersistence = new FakePickingPersistencePort();
        pickingConfirmationPersistence = new FakePickingConfirmationPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        outboxWriter = new FakeOutboxWriterPort();
        masterReadModel = new FakeMasterReadModelPort();
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence, fixedClock);
        service = new ConfirmPickingService(orderPersistence, pickingPersistence,
                pickingConfirmationPersistence, sagaPersistence, coordinator,
                outboxWriter, masterReadModel, fixedClock);

        orderId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        partnerId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        orderLineId = UUID.randomUUID();
        pickingRequestId = UUID.randomUUID();
        sagaId = UUID.randomUUID();

        masterReadModel.addSku(skuId, "SKU-001",
                SkuSnapshot.TrackingType.NONE, SkuSnapshot.Status.ACTIVE);
    }

    @Test
    void happyPath_advancesOrderToPicked_advancesSaga_writesOutbox() {
        seedOrderInPicking(50);
        seedPickingRequest(50);
        seedSaga(SagaStatus.RESERVED);

        ConfirmPickingCommand cmd = new ConfirmPickingCommand(
                orderId,
                "ok",
                List.of(new ConfirmPickingLineCommand(orderLineId, skuId, null, locationId, 50)),
                "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));

        PickingConfirmationResult result = service.confirm(cmd);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PICKED.name());
        assertThat(result.sagaState()).isEqualTo(SagaStatus.PICKING_CONFIRMED.name());
        assertThat(outboxWriter.countByType("outbound.picking.completed")).isEqualTo(1);
        assertThat(pickingConfirmationPersistence.saveCalls).isEqualTo(1);
        assertThat(orderPersistence.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PICKED);
        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.PICKING_CONFIRMED);
    }

    @Test
    void orderNotInPicking_raisesStateTransitionInvalid() {
        seedOrderInState(OrderStatus.RECEIVED, 50);
        seedPickingRequest(50);
        seedSaga(SagaStatus.REQUESTED);

        ConfirmPickingCommand cmd = new ConfirmPickingCommand(
                orderId, null,
                List.of(new ConfirmPickingLineCommand(orderLineId, skuId, null, locationId, 50)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.confirm(cmd))
                .isInstanceOf(StateTransitionInvalidException.class);
        assertThat(outboxWriter.published).isEmpty();
    }

    @Test
    void qtyMismatch_raisesPickingIncomplete() {
        seedOrderInPicking(50);
        seedPickingRequest(50);
        seedSaga(SagaStatus.RESERVED);

        ConfirmPickingCommand cmd = new ConfirmPickingCommand(
                orderId, null,
                List.of(new ConfirmPickingLineCommand(orderLineId, skuId, null, locationId,
                        49 /* not equal qty_ordered */)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.confirm(cmd))
                .isInstanceOf(PickingIncompleteException.class);
        assertThat(outboxWriter.published).isEmpty();
    }

    @Test
    void lotTrackedSkuWithoutLot_raisesLotRequired() {
        UUID lotSkuId = UUID.randomUUID();
        masterReadModel.addSku(lotSkuId, "SKU-LOT",
                SkuSnapshot.TrackingType.LOT, SkuSnapshot.Status.ACTIVE);
        seedOrderInPicking(lotSkuId, 50);
        seedPickingRequest(lotSkuId, 50);
        seedSaga(SagaStatus.RESERVED);

        ConfirmPickingCommand cmd = new ConfirmPickingCommand(
                orderId, null,
                List.of(new ConfirmPickingLineCommand(orderLineId, lotSkuId, null /* missing lot */,
                        locationId, 50)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.confirm(cmd))
                .isInstanceOf(LotRequiredException.class);
        assertThat(outboxWriter.published).isEmpty();
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private void seedOrderInPicking(int qty) {
        seedOrderInPicking(skuId, qty);
    }

    private void seedOrderInPicking(UUID actualSkuId, int qty) {
        seedOrderInState(OrderStatus.PICKING, actualSkuId, qty);
    }

    private void seedOrderInState(OrderStatus status, int qty) {
        seedOrderInState(status, skuId, qty);
    }

    private void seedOrderInState(OrderStatus status, UUID actualSkuId, int qty) {
        OrderLine line = new OrderLine(orderLineId, orderId, 1, actualSkuId, null, qty);
        Order order = new Order(orderId, "ORD-1", OrderSource.MANUAL,
                partnerId, warehouseId, null, null, status,
                0L, T0, "creator", T0, "creator", List.of(line));
        orderPersistence.save(order);
    }

    private void seedPickingRequest(int qty) {
        seedPickingRequest(skuId, qty);
    }

    private void seedPickingRequest(UUID actualSkuId, int qty) {
        PickingRequestLine line = new PickingRequestLine(
                UUID.randomUUID(), pickingRequestId, orderLineId, actualSkuId, null,
                locationId, qty);
        PickingRequest request = new PickingRequest(
                pickingRequestId, orderId, sagaId, warehouseId,
                PickingRequestStatus.SUBMITTED, 0L, T0, T0, List.of(line));
        pickingPersistence.save(request);
    }

    private void seedSaga(SagaStatus status) {
        OutboundSaga saga = new OutboundSaga(
                sagaId, orderId, status, pickingRequestId, null, T0, T0, 0L);
        sagaPersistence.save(saga);
    }
}
