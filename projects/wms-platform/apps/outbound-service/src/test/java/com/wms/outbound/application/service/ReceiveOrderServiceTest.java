package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.ReceiveOrderCommand;
import com.wms.outbound.application.command.ReceiveOrderLineCommand;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.service.fakes.FakeCallerScopeProvider;
import com.wms.outbound.application.service.fakes.FakeMasterReadModelPort;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.exception.PartnerInvalidTypeException;
import com.wms.outbound.domain.exception.SkuInactiveException;
import com.wms.outbound.domain.model.masterref.PartnerSnapshot;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import com.wms.outbound.domain.model.masterref.WarehouseSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiveOrderServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(T0, ZoneOffset.UTC);

    private FakeOrderPersistencePort orderPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOutboxWriterPort outboxWriter;
    private FakeMasterReadModelPort masterReadModel;
    private FakeCallerScopeProvider callerScope;
    private ReceiveOrderService service;

    private UUID partnerId;
    private UUID warehouseId;
    private UUID skuId;

    @BeforeEach
    void setUp() {
        orderPersistence = new FakeOrderPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        outboxWriter = new FakeOutboxWriterPort();
        masterReadModel = new FakeMasterReadModelPort();
        callerScope = new FakeCallerScopeProvider();
        service = new ReceiveOrderService(orderPersistence, sagaPersistence,
                outboxWriter, masterReadModel, callerScope, fixedClock);

        partnerId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        masterReadModel.addPartner(partnerId, "CUST-001",
                PartnerSnapshot.PartnerType.CUSTOMER, PartnerSnapshot.Status.ACTIVE);
        masterReadModel.addWarehouse(warehouseId, "WH-1",
                WarehouseSnapshot.Status.ACTIVE);
        masterReadModel.addSku(skuId, "SKU-001",
                SkuSnapshot.TrackingType.NONE, SkuSnapshot.Status.ACTIVE);
    }

    @Test
    void happyPathCreatesOrderSagaAndTwoOutboxRows() {
        ReceiveOrderCommand cmd = new ReceiveOrderCommand(
                "ORD-1",
                "MANUAL",
                partnerId,
                warehouseId,
                null,
                "notes",
                List.of(new ReceiveOrderLineCommand(1, skuId, null, 50)),
                "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));

        OrderResult result = service.receive(cmd);

        assertThat(result.status()).isEqualTo("PICKING");
        assertThat(result.sagaState()).isEqualTo("REQUESTED");
        assertThat(orderPersistence.orderCount()).isEqualTo(1);
        assertThat(sagaPersistence.sagaCount()).isEqualTo(1);
        assertThat(outboxWriter.countByType("outbound.order.received")).isEqualTo(1);
        assertThat(outboxWriter.countByType("outbound.picking.requested")).isEqualTo(1);
    }

    @Test
    void inactivePartnerRaisesPartnerInvalidType() {
        UUID inactiveId = UUID.randomUUID();
        masterReadModel.addPartner(inactiveId, "CUST-X",
                PartnerSnapshot.PartnerType.CUSTOMER, PartnerSnapshot.Status.INACTIVE);
        ReceiveOrderCommand cmd = new ReceiveOrderCommand(
                "ORD-2", "MANUAL", inactiveId, warehouseId, null, null,
                List.of(new ReceiveOrderLineCommand(1, skuId, null, 1)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.receive(cmd))
                .isInstanceOf(PartnerInvalidTypeException.class);
    }

    @Test
    void supplierTypePartnerIsRejected() {
        UUID supplierOnlyId = UUID.randomUUID();
        masterReadModel.addPartner(supplierOnlyId, "SUP-1",
                PartnerSnapshot.PartnerType.SUPPLIER, PartnerSnapshot.Status.ACTIVE);
        ReceiveOrderCommand cmd = new ReceiveOrderCommand(
                "ORD-3", "MANUAL", supplierOnlyId, warehouseId, null, null,
                List.of(new ReceiveOrderLineCommand(1, skuId, null, 1)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.receive(cmd))
                .isInstanceOf(PartnerInvalidTypeException.class);
    }

    @Test
    void inactiveSkuRaisesSkuInactive() {
        UUID inactiveSkuId = UUID.randomUUID();
        masterReadModel.addSku(inactiveSkuId, "SKU-X",
                SkuSnapshot.TrackingType.NONE, SkuSnapshot.Status.INACTIVE);
        ReceiveOrderCommand cmd = new ReceiveOrderCommand(
                "ORD-4", "MANUAL", partnerId, warehouseId, null, null,
                List.of(new ReceiveOrderLineCommand(1, inactiveSkuId, null, 1)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.receive(cmd))
                .isInstanceOf(SkuInactiveException.class);
    }

    // ------------------------------------------------------------------
    //  ADR-MONO-064 § D1 — the create path stamps the caller's own tenant
    // ------------------------------------------------------------------

    private ReceiveOrderCommand manualOrder(String orderNo) {
        return new ReceiveOrderCommand(
                orderNo, "MANUAL", partnerId, warehouseId, null, null,
                List.of(new ReceiveOrderLineCommand(1, skuId, null, 5)),
                "user-1", Set.of("ROLE_OUTBOUND_WRITE"));
    }

    private String persistedTenantOf(String orderNo) {
        return orderPersistence.findByOrderNo(orderNo).orElseThrow().getTenantId();
    }

    /**
     * The defect this decision exists to close. A tenant-scoped operator used to create
     * an order with {@code tenant_id = NULL}, which its own {@code CallerScope} could
     * never match — so it could not read, pick, pack, ship or cancel what it had just
     * created (the create path is the only operation with no prior order to guard).
     */
    @Test
    void restrictedCaller_stampsItsOwnTenantOnTheOrder() {
        callerScope.restrictTo("demo-corp");

        service.receive(manualOrder("ORD-T1"));

        assertThat(persistedTenantOf("ORD-T1"))
                .as("§ D1: a restricted caller owns what it creates")
                .isEqualTo("demo-corp");
    }

    /**
     * The unrestricted half must not move: a native wms operator and every internal
     * flow (Kafka consumer / scheduler / webhook inbox processor) keep producing
     * tenant-less B2B orders exactly as before this decision.
     */
    @Test
    void unrestrictedCaller_leavesTheOrderTenantless() {
        callerScope.unrestrict();

        service.receive(manualOrder("ORD-T2"));

        assertThat(persistedTenantOf("ORD-T2")).isNull();
    }

    /**
     * Precedence: the event plane is authoritative. {@code FulfillmentRequestedConsumer}
     * carries the ecommerce tenant explicitly (ADR-MONO-022 facet d) and runs with no
     * security context, so nothing here may overwrite it. Pinned with a caller scope
     * that would stamp a <em>different</em> value, so the assertion fails if the
     * precedence is ever inverted — a control that a plain unrestricted run could not
     * give (both branches would produce the same answer).
     */
    @Test
    void tenantAlreadyOnTheCommand_isNotOverwritten() {
        callerScope.restrictTo("demo-corp");
        ReceiveOrderCommand fromEventPlane = new ReceiveOrderCommand(
                "ORD-T3",
                "FULFILLMENT_ECOMMERCE",
                partnerId,
                warehouseId,
                null,
                null,
                null /* shipTo */,
                "ecommerce" /* opaque correlation carried by the consumer */,
                List.of(new ReceiveOrderLineCommand(1, skuId, null, 5)),
                "system:fulfillment",
                Set.of());

        service.receive(fromEventPlane);

        assertThat(persistedTenantOf("ORD-T3"))
                .as("the command's explicit tenant wins over the caller scope")
                .isEqualTo("ecommerce");
    }
}
