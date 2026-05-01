package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.ConfirmPackingCommand;
import com.wms.outbound.application.command.CreatePackingUnitCommand;
import com.wms.outbound.application.command.CreatePackingUnitLineCommand;
import com.wms.outbound.application.command.SealPackingUnitCommand;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.result.PackingUnitResult;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakePackingPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.exception.PackingIncompleteException;
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
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackingServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(T0, ZoneOffset.UTC);

    private FakeOrderPersistencePort orderPersistence;
    private FakePackingPersistencePort packingPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOutboxWriterPort outboxWriter;
    private OutboundSagaCoordinator coordinator;
    private PackingService service;

    private UUID orderId;
    private UUID skuId;
    private UUID warehouseId;
    private UUID partnerId;
    private UUID orderLineId;
    private UUID sagaId;

    @BeforeEach
    void setUp() {
        orderPersistence = new FakeOrderPersistencePort();
        packingPersistence = new FakePackingPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        outboxWriter = new FakeOutboxWriterPort();
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence, fixedClock);
        service = new PackingService(orderPersistence, packingPersistence,
                sagaPersistence, coordinator, outboxWriter, fixedClock);

        orderId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        partnerId = UUID.randomUUID();
        orderLineId = UUID.randomUUID();
        sagaId = UUID.randomUUID();
    }

    @Test
    void createPackingUnit_pickedOrderTransitionsToPacking_andUnitCreated() {
        seedOrder(OrderStatus.PICKED, 50);
        seedSaga(SagaStatus.PICKING_CONFIRMED);

        CreatePackingUnitCommand cmd = new CreatePackingUnitCommand(
                orderId, "BOX-001", "BOX", 1000, 100, 100, 100, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 50)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        PackingUnitResult result = service.create(cmd);

        assertThat(result.status()).isEqualTo(PackingUnitStatus.OPEN.name());
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PACKING.name());
        assertThat(packingPersistence.count()).isEqualTo(1);
        assertThat(orderPersistence.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PACKING);
    }

    @Test
    void sealPackingUnit_openTransitionsToSealed() {
        seedOrder(OrderStatus.PACKING, 50);
        seedSaga(SagaStatus.PICKING_CONFIRMED);

        // Create + seal.
        CreatePackingUnitCommand create = new CreatePackingUnitCommand(
                orderId, "BOX-001", "BOX", null, null, null, null, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 50)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));
        PackingUnitResult created = service.create(create);

        SealPackingUnitCommand seal = new SealPackingUnitCommand(
                orderId, created.packingUnitId(), 0L, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));
        PackingUnitResult sealed = service.seal(seal);

        assertThat(sealed.status()).isEqualTo(PackingUnitStatus.SEALED.name());
    }

    @Test
    void confirmPacking_allUnitsSealed_advancesOrderToPackedAndWritesOutbox() {
        // Legacy fallback path: seal-completes-packing (AC-01) is the
        // canonical trigger now, but the explicit confirm endpoint remains
        // available for callers that pre-seal units out-of-band. Inject a
        // sealed unit directly so we exercise the confirm path on a still-
        // PACKING order without the seal service preempting it.
        seedOrder(OrderStatus.PACKING, 50);
        seedSaga(SagaStatus.PICKING_CONFIRMED);
        seedSealedUnit("BOX-001", 50);

        OrderResult result = service.confirm(new ConfirmPackingCommand(
                orderId, -1L, "user-1", Set.of("ROLE_OUTBOUND_WRITE")));

        assertThat(result.status()).isEqualTo(OrderStatus.PACKED.name());
        assertThat(result.sagaState()).isEqualTo(SagaStatus.PACKING_CONFIRMED.name());
        assertThat(outboxWriter.countByType("outbound.packing.completed")).isEqualTo(1);
    }

    @Test
    void confirmPacking_unsealedUnit_raisesPackingIncomplete() {
        seedOrder(OrderStatus.PACKING, 50);
        seedSaga(SagaStatus.PICKING_CONFIRMED);
        service.create(new CreatePackingUnitCommand(
                orderId, "BOX-001", "BOX", null, null, null, null, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 50)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE")));
        // Note: NOT sealing.

        assertThatThrownBy(() -> service.confirm(new ConfirmPackingCommand(
                orderId, -1L, "user-1", Set.of("ROLE_OUTBOUND_WRITE"))))
                .isInstanceOf(PackingIncompleteException.class);
        assertThat(outboxWriter.published).isEmpty();
    }

    @Test
    void confirmPacking_qtySumLessThanOrdered_raisesPackingIncomplete() {
        seedOrder(OrderStatus.PACKING, 50);
        seedSaga(SagaStatus.PICKING_CONFIRMED);
        // Pack only 30 of 50.
        PackingUnitResult c = service.create(new CreatePackingUnitCommand(
                orderId, "BOX-001", "BOX", null, null, null, null, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 30)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE")));
        service.seal(new SealPackingUnitCommand(
                orderId, c.packingUnitId(), 0L, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE")));

        assertThatThrownBy(() -> service.confirm(new ConfirmPackingCommand(
                orderId, -1L, "user-1", Set.of("ROLE_OUTBOUND_WRITE"))))
                .isInstanceOf(PackingIncompleteException.class);
    }

    @Test
    void createPackingUnit_orderInReceived_raisesStateTransitionInvalid() {
        seedOrder(OrderStatus.RECEIVED, 50);

        CreatePackingUnitCommand cmd = new CreatePackingUnitCommand(
                orderId, "BOX-001", "BOX", null, null, null, null, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 50)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    // ------------------------------------------------------------------
    //  TASK-BE-040 AC-01 / AC-02: seal-completes-packing (PATCH seal flow)
    // ------------------------------------------------------------------

    /**
     * AC-01: sealing the only OPEN unit, when the per-orderLineId qty sum
     * covers every order line, atomically transitions the Order to
     * {@code PACKED}, advances the saga to {@code PACKING_CONFIRMED}, and
     * writes the {@code outbound.packing.completed} outbox row.
     */
    @Test
    void sealLastUnit_whenAllQtyCoversAllLines_completesPackingAndWritesOutbox() {
        seedOrder(OrderStatus.PACKING, 5);
        seedSaga(SagaStatus.PICKING_CONFIRMED);

        CreatePackingUnitCommand create = new CreatePackingUnitCommand(
                orderId, "BOX-001", "BOX", null, null, null, null, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 5)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));
        PackingUnitResult created = service.create(create);

        SealPackingUnitCommand seal = new SealPackingUnitCommand(
                orderId, created.packingUnitId(), 0L, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));
        PackingUnitResult sealed = service.seal(seal);

        assertThat(sealed.status()).isEqualTo(PackingUnitStatus.SEALED.name());
        assertThat(sealed.orderStatus()).isEqualTo(OrderStatus.PACKED.name());
        assertThat(orderPersistence.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PACKED);
        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.PACKING_CONFIRMED);
        assertThat(outboxWriter.countByType("outbound.packing.completed")).isEqualTo(1);
    }

    /**
     * AC-02: sealing a non-final unit (other unit still OPEN) succeeds with
     * the unit transitioning to {@code SEALED} but emits NO outbox event and
     * does not advance the order or saga.
     */
    @Test
    void sealNonLastUnit_doesNotEmitEvent() {
        seedOrder(OrderStatus.PACKING, 10);
        seedSaga(SagaStatus.PICKING_CONFIRMED);

        // Two units: each carries half the line qty.
        PackingUnitResult unitA = service.create(new CreatePackingUnitCommand(
                orderId, "BOX-001", "BOX", null, null, null, null, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 5)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE")));
        service.create(new CreatePackingUnitCommand(
                orderId, "BOX-002", "BOX", null, null, null, null, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 5)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE")));

        // Seal only the first unit; the second remains OPEN.
        PackingUnitResult sealed = service.seal(new SealPackingUnitCommand(
                orderId, unitA.packingUnitId(), 0L, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE")));

        assertThat(sealed.status()).isEqualTo(PackingUnitStatus.SEALED.name());
        assertThat(sealed.orderStatus()).isEqualTo(OrderStatus.PACKING.name());
        assertThat(orderPersistence.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PACKING);
        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.PICKING_CONFIRMED);
        assertThat(outboxWriter.countByType("outbound.packing.completed")).isEqualTo(0);
    }

    /**
     * AC-02 corollary: if the final unit seals but its lines do not yet cover
     * every order-line qty, the unit transitions to SEALED but no order/saga
     * transition or outbox emission occurs (operator must add another unit).
     */
    @Test
    void sealLastUnit_whenQtyNotCovered_doesNotComplete() {
        seedOrder(OrderStatus.PACKING, 5);
        seedSaga(SagaStatus.PICKING_CONFIRMED);

        // Single unit packing only 3 of 5.
        PackingUnitResult created = service.create(new CreatePackingUnitCommand(
                orderId, "BOX-001", "BOX", null, null, null, null, null,
                List.of(new CreatePackingUnitLineCommand(orderLineId, skuId, null, 3)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE")));
        PackingUnitResult sealed = service.seal(new SealPackingUnitCommand(
                orderId, created.packingUnitId(), 0L, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE")));

        assertThat(sealed.status()).isEqualTo(PackingUnitStatus.SEALED.name());
        assertThat(sealed.orderStatus()).isEqualTo(OrderStatus.PACKING.name());
        assertThat(orderPersistence.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PACKING);
        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.PICKING_CONFIRMED);
        assertThat(outboxWriter.countByType("outbound.packing.completed")).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private void seedOrder(OrderStatus status, int qty) {
        OrderLine line = new OrderLine(orderLineId, orderId, 1, skuId, null, qty);
        Order order = new Order(orderId, "ORD-1", OrderSource.MANUAL,
                partnerId, warehouseId, null, null, status,
                0L, T0, "creator", T0, "creator", List.of(line));
        orderPersistence.save(order);
    }

    private void seedSaga(SagaStatus status) {
        OutboundSaga saga = new OutboundSaga(
                sagaId, orderId, status, sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);
    }

    /**
     * Inserts a SEALED PackingUnit directly so tests of the legacy
     * confirm-packing fallback path can exercise it without going through
     * the seal use-case (which would now itself drive completion).
     */
    private void seedSealedUnit(String cartonNo, int qty) {
        UUID unitId = UUID.randomUUID();
        PackingUnitLine line = new PackingUnitLine(
                UUID.randomUUID(), unitId, orderLineId, skuId, null, qty);
        PackingUnit unit = new PackingUnit(
                unitId, orderId, cartonNo, PackingType.BOX,
                null, null, null, null, null,
                PackingUnitStatus.SEALED, 0L, T0, T0, List.of(line));
        packingPersistence.save(unit);
    }
}
