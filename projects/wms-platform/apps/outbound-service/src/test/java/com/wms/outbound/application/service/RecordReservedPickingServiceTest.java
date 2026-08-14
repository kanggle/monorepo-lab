package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.RecordReservedPickingCommand;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakePickingPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.PickingRequestLine;
import com.wms.outbound.domain.model.PickingRequestStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TASK-BE-586 / ADR-MONO-066 (ACCEPTED, option B).
 *
 * <p>🔴 <b>Read this before relaxing any assertion here.</b> The defect these
 * cells exist for was not a wrong value — it was a <em>missing row</em>. Nothing
 * in production ever called {@code PickingPersistencePort.save} (0 production
 * callers, 2 test callers), so every outbound order stopped at {@code PICKING},
 * and the two tests that did call it seeded the row themselves and verified the
 * downstream on top of it. The suite was green on a state the product could not
 * produce. A cell that tolerates "no picking request" would restore exactly that
 * blindness, which is why {@link #reservedEventCreatesThePickingRequest()}
 * asserts the row's existence first and its contents second.
 */
@DisplayName("inventory.reserved materialises the PickingRequest (ADR-MONO-066 B)")
class RecordReservedPickingServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private final UUID orderId = UUID.randomUUID();
    private final UUID sagaId = UUID.randomUUID();
    private final UUID warehouseId = UUID.randomUUID();
    private final UUID skuA = UUID.randomUUID();
    private final UUID skuB = UUID.randomUUID();
    private final UUID locationA = UUID.randomUUID();
    private final UUID locationB = UUID.randomUUID();

    private FakePickingPersistencePort pickingPersistence;
    private FakeOrderPersistencePort orderPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private RecordReservedPickingService service;

    @BeforeEach
    void setUp() {
        pickingPersistence = new FakePickingPersistencePort();
        orderPersistence = new FakeOrderPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        service = new RecordReservedPickingService(
                pickingPersistence, orderPersistence, sagaPersistence, clock);
        sagaPersistence.save(OutboundSaga.newRequested(sagaId, orderId, T0));
    }

    @Test
    @DisplayName("creates the row, keyed by the echoed reservation id, with inventory's locations")
    void reservedEventCreatesThePickingRequest() {
        List<OrderLine> lines = seedOrder(skuA, skuB);

        service.recordReservedPicking(new RecordReservedPickingCommand(
                sagaId, sagaId, warehouseId,
                List.of(reserved(skuA, locationA, 3), reserved(skuB, locationB, 5))));

        PickingRequest saved = pickingPersistence.findByOrderId(orderId).orElseThrow(
                () -> new AssertionError("no PickingRequest was created — this is the whole defect"));

        assertThat(saved.getId()).isEqualTo(sagaId); // == the echoed pickingRequestId
        assertThat(saved.getSagaId()).isEqualTo(sagaId);
        assertThat(saved.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(saved.getStatus()).isEqualTo(PickingRequestStatus.SUBMITTED);

        assertThat(saved.getLines()).hasSize(2);
        // The location is inventory's, and it is attached to the right order line
        // — that pairing is the thing that cannot be checked by counting rows.
        assertThat(saved.getLines())
                .extracting(PickingRequestLine::getOrderLineId,
                        PickingRequestLine::getSkuId,
                        PickingRequestLine::getLocationId,
                        PickingRequestLine::getQtyToPick)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(lines.get(0).getId(), skuA, locationA, 3),
                        org.assertj.core.groups.Tuple.tuple(lines.get(1).getId(), skuB, locationB, 5));
    }

    @Test
    @DisplayName("repeated (skuId, lotId) lines are matched positionally, not collapsed")
    void repeatedSkuLinesAreMatchedInOrder() {
        // Two order lines for the same SKU with no lot — the join key alone
        // cannot separate them, so order has to.
        OrderLine first = new OrderLine(UUID.randomUUID(), orderId, 1, skuA, null, 2);
        OrderLine second = new OrderLine(UUID.randomUUID(), orderId, 2, skuA, null, 7);
        saveOrder(List.of(first, second));

        service.recordReservedPicking(new RecordReservedPickingCommand(
                sagaId, sagaId, warehouseId,
                List.of(reserved(skuA, locationA, 2), reserved(skuA, locationB, 7))));

        PickingRequest saved = pickingPersistence.findByOrderId(orderId).orElseThrow();
        assertThat(saved.getLines())
                .extracting(PickingRequestLine::getOrderLineId, PickingRequestLine::getLocationId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first.getId(), locationA),
                        org.assertj.core.groups.Tuple.tuple(second.getId(), locationB));
    }

    @Test
    @DisplayName("a second reservation reply does not create a second request")
    void isIdempotentPerOrder() {
        seedOrder(skuA, skuB);
        RecordReservedPickingCommand cmd = new RecordReservedPickingCommand(
                sagaId, sagaId, warehouseId,
                List.of(reserved(skuA, locationA, 3), reserved(skuB, locationB, 5)));

        service.recordReservedPicking(cmd);
        service.recordReservedPicking(cmd);

        assertThat(pickingPersistence.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a line count that disagrees with the order throws instead of writing a wrong row")
    void lineCountMismatchThrows() {
        seedOrder(skuA, skuB);

        assertThatThrownBy(() -> service.recordReservedPicking(new RecordReservedPickingCommand(
                sagaId, sagaId, warehouseId, List.of(reserved(skuA, locationA, 3)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line count does not match");

        assertThat(pickingPersistence.count()).isZero();
    }

    @Test
    @DisplayName("an unmatched sku throws instead of guessing an order line")
    void unmatchedSkuThrows() {
        seedOrder(skuA, skuB);

        assertThatThrownBy(() -> service.recordReservedPicking(new RecordReservedPickingCommand(
                sagaId, sagaId, warehouseId,
                List.of(reserved(skuA, locationA, 3),
                        reserved(UUID.randomUUID(), locationB, 5)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no matching order line");

        assertThat(pickingPersistence.count()).isZero();
    }

    @Test
    @DisplayName("an unknown saga is ignored, not a crash")
    void unknownSagaIsIgnored() {
        service.recordReservedPicking(new RecordReservedPickingCommand(
                UUID.randomUUID(), UUID.randomUUID(), warehouseId,
                List.of(reserved(skuA, locationA, 3))));

        assertThat(pickingPersistence.count()).isZero();
    }

    // --- helpers -------------------------------------------------------------

    private static RecordReservedPickingCommand.Line reserved(UUID skuId, UUID locationId, int qty) {
        return new RecordReservedPickingCommand.Line(skuId, null, locationId, qty);
    }

    private List<OrderLine> seedOrder(UUID firstSku, UUID secondSku) {
        List<OrderLine> lines = List.of(
                new OrderLine(UUID.randomUUID(), orderId, 1, firstSku, null, 3),
                new OrderLine(UUID.randomUUID(), orderId, 2, secondSku, null, 5));
        saveOrder(lines);
        return lines;
    }

    private void saveOrder(List<OrderLine> lines) {
        orderPersistence.save(new Order(orderId, "SO-1", OrderSource.MANUAL,
                UUID.randomUUID(), warehouseId, null, null, OrderStatus.PICKING,
                0L, T0, "creator", T0, "creator", lines));
    }
}
