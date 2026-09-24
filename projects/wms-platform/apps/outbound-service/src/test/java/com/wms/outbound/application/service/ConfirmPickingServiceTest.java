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
import com.wms.outbound.domain.exception.LocationInactiveException;
import com.wms.outbound.domain.exception.LotRequiredException;
import com.wms.outbound.domain.exception.LotSubstitutionNotAllowedException;
import com.wms.outbound.domain.exception.OrderLineMismatchException;
import com.wms.outbound.domain.exception.OutboundDomainException;
import com.wms.outbound.domain.exception.PickingIncompleteException;
import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import com.wms.outbound.domain.exception.WarehouseMismatchException;
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
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence, outboxWriter, fixedClock);
        service = new ConfirmPickingService(orderPersistence, pickingPersistence,
                pickingConfirmationPersistence, sagaPersistence, coordinator,
                outboxWriter, masterReadModel,
                new com.wms.outbound.application.service.fakes.FakeCallerScopeProvider(),
                fixedClock);

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

    /**
     * TASK-MONO-724 — 계획 lot 이 구체(A)인 라인을 다른 구체 lot(B)으로 확정하면 <b>거절</b>한다.
     * 🔴 AC-0 에서 이 칸은 «받아들여진다» 를 고정한 측정이었다(그때 통과 = 대체 도달 가능).
     * 소유자 결정 ① 로 <b>의도적으로</b> 뒤집었다: inventory 는 A 를 예약했고
     * {@code ConfirmShippingService} 는 출하 lot 을 이 확정에서 가져오므로, B 가 통과하면
     * {@code shipping.confirmed} 가 B 를 싣고 inventory 가 매칭하지 못해 DLT 로 간다(§C4, 706).
     */
    @Test
    void concreteLotSubstitution_isRejected_andNothingIsWritten() {
        UUID lotSkuId = lotTrackedSku();
        UUID plannedLot = UUID.randomUUID();
        UUID pickedLot = UUID.randomUUID();
        seedLotPlannedOrder(lotSkuId, plannedLot);

        assertThatThrownBy(() -> service.confirm(lotConfirm(lotSkuId, pickedLot)))
                .isInstanceOf(LotSubstitutionNotAllowedException.class)
                .satisfies(e -> assertThat(((LotSubstitutionNotAllowedException) e).errorCode())
                        .isEqualTo("LOT_SUBSTITUTION_NOT_ALLOWED"));
        assertThat(outboxWriter.published).isEmpty();
        assertThat(pickingConfirmationPersistence.saveCalls).isZero();
        assertThat(orderPersistence.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PICKING);
    }

    /** 경계 핀: 계획 lot 과 <b>같은</b> lot 으로 확정하면 통과한다. */
    @Test
    void confirmingThePlannedLot_isAccepted() {
        UUID lotSkuId = lotTrackedSku();
        UUID plannedLot = UUID.randomUUID();
        seedLotPlannedOrder(lotSkuId, plannedLot);

        PickingConfirmationResult result = service.confirm(lotConfirm(lotSkuId, plannedLot));

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PICKED.name());
        assertThat(result.lines()).singleElement()
                .satisfies(l -> assertThat(l.lotId()).isEqualTo(plannedLot));
    }

    /**
     * 경계 핀: 계획 lot 이 NULL(any-lot)이면 대체가 아니다 — 운영자가 여기서 실물 lot 을 정하고
     * 통과한다. 🔴 데모 시드의 흐름이 정확히 이것이다(TASK-MONO-706) — 이 칸이 빨개지면 데모가 멈춘다.
     */
    @Test
    void anyLotOrderLine_acceptsTheOperatorBoundLot() {
        UUID lotSkuId = lotTrackedSku();
        UUID boundLot = UUID.randomUUID();
        seedLotPlannedOrder(lotSkuId, null);

        PickingConfirmationResult result = service.confirm(lotConfirm(lotSkuId, boundLot));

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PICKED.name());
        assertThat(result.lines()).singleElement()
                .satisfies(l -> assertThat(l.lotId()).isEqualTo(boundLot));
    }

    // ------------------------------------------------------------------
    //  TASK-BE-596 — 계약 §2.3: skuId 는 주문 라인의 것, actualLocationId 는 ACTIVE·같은 창고
    //  🔴 AC-0 에서 아래 거절 칸들은 고치기 전 트리에서 빨갛다(요청 값이 그대로 저장·발행됐다).
    //  오류 코드는 AC-1 소유자 결정: 새 ORDER_LINE_MISMATCH(422) · 위치는 기존 WAREHOUSE_MISMATCH / LOCATION_INACTIVE.
    // ------------------------------------------------------------------

    @Test
    void skuThatIsNotTheOrderLinesSku_isRejected_andNothingIsWritten() {
        UUID otherSku = UUID.randomUUID();
        masterReadModel.addSku(otherSku, "SKU-OTHER",
                SkuSnapshot.TrackingType.NONE, SkuSnapshot.Status.ACTIVE);
        seedOrderInPicking(50);
        seedPickingRequest(50);
        seedSaga(SagaStatus.RESERVED);

        assertRejectedAndNothingWritten(new ConfirmPickingLineCommand(
                orderLineId, otherSku, null, locationId, 50),
                OrderLineMismatchException.class, "ORDER_LINE_MISMATCH");
    }

    /**
     * 🔴 가장 나쁜 모양: 주문 라인은 LOT 추적인데 요청에 <b>비LOT SKU</b> 를 적으면, LOT 필수 판정이
     * 요청 SKU 로 찾기 때문에 {@code LOT_REQUIRED} 가 걸리지 않았다.
     */
    @Test
    void lotRequirement_isNotBypassedByNamingANonLotSku() {
        UUID lotSkuId = lotTrackedSku();
        seedLotPlannedOrder(lotSkuId, null);

        // The SKU mismatch is caught first; what matters is that nothing gets through.
        // The LOT judgement itself now reads the order line's SKU (ConfirmPickingService).
        assertRejectedAndNothingWritten(new ConfirmPickingLineCommand(
                orderLineId, skuId /* NONE-tracked */, null /* no lot */, locationId, 50),
                OrderLineMismatchException.class, "ORDER_LINE_MISMATCH");
    }

    @Test
    void locationInAnotherWarehouse_isRejected_andNothingIsWritten() {
        masterReadModel.addLocation(locationId, "OTHER-WH-A-01", UUID.randomUUID(),
                com.wms.outbound.domain.model.masterref.LocationSnapshot.Status.ACTIVE);
        seedOrderInPicking(50);
        seedPickingRequest(50);
        seedSaga(SagaStatus.RESERVED);

        assertRejectedAndNothingWritten(new ConfirmPickingLineCommand(
                orderLineId, skuId, null, locationId, 50),
                WarehouseMismatchException.class, "WAREHOUSE_MISMATCH");
    }

    @Test
    void inactiveLocation_isRejected_andNothingIsWritten() {
        masterReadModel.addLocation(locationId, "WH-A-01", warehouseId,
                com.wms.outbound.domain.model.masterref.LocationSnapshot.Status.INACTIVE);
        seedOrderInPicking(50);
        seedPickingRequest(50);
        seedSaga(SagaStatus.RESERVED);

        assertRejectedAndNothingWritten(new ConfirmPickingLineCommand(
                orderLineId, skuId, null, locationId, 50),
                LocationInactiveException.class, "LOCATION_INACTIVE");
    }

    /** 대조군: 등록된 ACTIVE·같은 창고 위치 + 주문 라인의 SKU 는 통과한다(«전부 거절» 이 아님). */
    @Test
    void matchingSkuAndActiveLocationInTheSameWarehouse_isAccepted() {
        masterReadModel.addLocation(locationId, "WH-A-01", warehouseId,
                com.wms.outbound.domain.model.masterref.LocationSnapshot.Status.ACTIVE);
        seedOrderInPicking(50);
        seedPickingRequest(50);
        seedSaga(SagaStatus.RESERVED);

        PickingConfirmationResult result = service.confirm(new ConfirmPickingCommand(
                orderId, null,
                List.of(new ConfirmPickingLineCommand(orderLineId, skuId, null, locationId, 50)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE")));

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PICKED.name());
        assertThat(result.lines()).singleElement()
                .satisfies(l -> assertThat(l.skuId()).isEqualTo(skuId));
    }

    private void assertRejectedAndNothingWritten(ConfirmPickingLineCommand line,
                                                 Class<? extends RuntimeException> type, String code) {
        ConfirmPickingCommand cmd = new ConfirmPickingCommand(
                orderId, null, List.of(line), "user-1", Set.of("ROLE_OUTBOUND_WRITE"));
        assertThatThrownBy(() -> service.confirm(cmd))
                .isInstanceOf(type)
                .satisfies(e -> assertThat(((OutboundDomainException) e).errorCode()).isEqualTo(code));
        assertThat(outboxWriter.published).isEmpty();
        assertThat(pickingConfirmationPersistence.saveCalls).isZero();
        assertThat(orderPersistence.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PICKING);
    }

    private UUID lotTrackedSku() {
        UUID lotSkuId = UUID.randomUUID();
        masterReadModel.addSku(lotSkuId, "SKU-LOT",
                SkuSnapshot.TrackingType.LOT, SkuSnapshot.Status.ACTIVE);
        return lotSkuId;
    }

    private void seedLotPlannedOrder(UUID lotSkuId, UUID plannedLot) {
        orderPersistence.save(new Order(orderId, "ORD-1", OrderSource.MANUAL,
                partnerId, warehouseId, null, null, OrderStatus.PICKING,
                0L, T0, "creator", T0, "creator",
                List.of(new OrderLine(orderLineId, orderId, 1, lotSkuId, null, plannedLot, 50))));
        pickingPersistence.save(new PickingRequest(
                pickingRequestId, orderId, sagaId, warehouseId,
                PickingRequestStatus.SUBMITTED, 0L, T0, T0,
                List.of(new PickingRequestLine(UUID.randomUUID(), pickingRequestId, orderLineId,
                        lotSkuId, plannedLot, locationId, 50))));
        seedSaga(SagaStatus.RESERVED);
    }

    private ConfirmPickingCommand lotConfirm(UUID lotSkuId, UUID confirmedLot) {
        return new ConfirmPickingCommand(
                orderId, null,
                List.of(new ConfirmPickingLineCommand(orderLineId, lotSkuId, confirmedLot, locationId, 50)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));
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
        OrderLine line = new OrderLine(orderLineId, orderId, 1, actualSkuId, null, null, qty);
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
