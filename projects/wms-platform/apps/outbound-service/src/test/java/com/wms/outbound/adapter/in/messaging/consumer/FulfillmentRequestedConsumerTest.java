package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.service.ReceiveOrderService;
import com.wms.outbound.application.service.fakes.FakeMasterReadModelPort;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.event.OrderReceivedEvent;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.masterref.PartnerSnapshot;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import com.wms.outbound.domain.model.masterref.WarehouseSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link FulfillmentRequestedConsumer} — mirrors
 * {@code InventoryReservedConsumerTest} (fakes, no Spring, no Kafka).
 *
 * <p>The consumer is wired against a real {@link ReceiveOrderService} backed by
 * fakes so the test exercises the full code→uuid resolution + order-intake path
 * end-to-end at the unit level.
 */
class FulfillmentRequestedConsumerTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private FakeOrderPersistencePort orderPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOutboxWriterPort outboxWriter;
    private FakeMasterReadModelPort masterReadModel;
    private FakeEventDedupePort dedupePort;
    private FulfillmentRequestedConsumer consumer;

    private UUID partnerId;
    private UUID warehouseId;
    private UUID skuId;

    @BeforeEach
    void setUp() {
        orderPersistence = new FakeOrderPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        outboxWriter = new FakeOutboxWriterPort();
        masterReadModel = new FakeMasterReadModelPort();
        dedupePort = new FakeEventDedupePort();

        ReceiveOrderService receiveOrderService = new ReceiveOrderService(
                orderPersistence, sagaPersistence, outboxWriter, masterReadModel, clock);
        consumer = new FulfillmentRequestedConsumer(
                new EventEnvelopeParser(new ObjectMapper()),
                dedupePort,
                receiveOrderService,
                masterReadModel);

        partnerId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        masterReadModel.addPartner(partnerId, "ECOMMERCE-STORE",
                PartnerSnapshot.PartnerType.CUSTOMER, PartnerSnapshot.Status.ACTIVE);
        masterReadModel.addWarehouse(warehouseId, "WH-MAIN",
                WarehouseSnapshot.Status.ACTIVE);
        masterReadModel.addSku(skuId, "SKU-APPLE-001",
                SkuSnapshot.TrackingType.NONE, SkuSnapshot.Status.ACTIVE);
    }

    private static String event(UUID eventId, String orderNo,
                                String partnerCode, String warehouseCode, String skuCode) {
        return event(eventId, orderNo, partnerCode, warehouseCode, skuCode, "store-acme");
    }

    /**
     * Builds the fulfillment envelope. {@code tenantId} is the envelope-level
     * opaque correlation (ADR-MONO-022 facet d); pass {@code null} to omit it
     * (standalone / pre-M5 producer).
     */
    private static String event(UUID eventId, String orderNo,
                                String partnerCode, String warehouseCode, String skuCode,
                                String tenantId) {
        String tenantLine = tenantId == null ? "" : "\"tenantId\": \"" + tenantId + "\",";
        return """
                {
                  "eventId": "%s",
                  "eventType": "ecommerce.fulfillment.requested",
                  "occurredAt": "2026-04-29T10:00:00.000Z",
                  "aggregateId": "%s",
                  "aggregateType": "fulfillment",
                  %s
                  "payload": {
                    "orderNo": "%s",
                    "customerPartnerCode": "%s",
                    "warehouseCode": "%s",
                    "requiredShipDate": null,
                    "shipTo": {
                      "recipientName": "홍길동",
                      "address": "서울시 강남구 1",
                      "phone": "010-1234-5678"
                    },
                    "lines": [
                      { "lineNo": 1, "skuCode": "%s", "lotNo": null, "qtyOrdered": 2 }
                    ]
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), tenantLine, orderNo,
                        partnerCode, warehouseCode, skuCode);
    }

    @Test
    void validEventCreatesOrderWithShipToAndFulfillmentSource() {
        String json = event(UUID.randomUUID(), "ECO-1001",
                "ECOMMERCE-STORE", "WH-MAIN", "SKU-APPLE-001");

        consumer.onMessage(json, null);

        assertThat(orderPersistence.orderCount()).isEqualTo(1);
        Order order = orderPersistence.findSummaries(null).stream()
                .findFirst()
                .flatMap(s -> orderPersistence.findById(s.orderId()))
                .orElseThrow();
        assertThat(order.getSource()).isEqualTo(OrderSource.FULFILLMENT_ECOMMERCE);
        assertThat(order.getOrderNo()).isEqualTo("ECO-1001");
        assertThat(order.getCustomerPartnerId()).isEqualTo(partnerId);
        assertThat(order.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(order.getShipTo()).isNotNull();
        assertThat(order.getShipTo().recipientName()).isEqualTo("홍길동");
        assertThat(order.getShipTo().address()).isEqualTo("서울시 강남구 1");
        assertThat(order.getShipTo().phone()).isEqualTo("010-1234-5678");
        // The envelope-level tenantId is captured as an opaque correlation on the
        // order (ADR-MONO-022 facet d) so the return-leg events can echo it.
        assertThat(order.getTenantId()).isEqualTo("store-acme");

        // order.received carries the additive shipTo; picking.requested also fired.
        assertThat(outboxWriter.countByType("outbound.order.received")).isEqualTo(1);
        assertThat(outboxWriter.countByType("outbound.picking.requested")).isEqualTo(1);
        OrderReceivedEvent received = (OrderReceivedEvent) outboxWriter.published.stream()
                .filter(e -> e.eventType().equals("outbound.order.received"))
                .findFirst().orElseThrow();
        assertThat(received.source()).isEqualTo("FULFILLMENT_ECOMMERCE");
        assertThat(received.shipTo()).isNotNull();
        assertThat(received.shipTo().recipientName()).isEqualTo("홍길동");
    }

    @Test
    void absentTenantIdYieldsNullCorrelation() {
        // Standalone ecommerce / pre-M5 producer: no envelope tenantId. wms must
        // accept the order with a null correlation (D8 net-zero), NOT reject it.
        String json = event(UUID.randomUUID(), "ECO-1002",
                "ECOMMERCE-STORE", "WH-MAIN", "SKU-APPLE-001", null);

        consumer.onMessage(json, null);

        assertThat(orderPersistence.orderCount()).isEqualTo(1);
        Order order = orderPersistence.findSummaries(null).stream()
                .findFirst()
                .flatMap(s -> orderPersistence.findById(s.orderId()))
                .orElseThrow();
        assertThat(order.getTenantId()).isNull();
    }

    @Test
    void duplicateEventIdIsSkipped() {
        UUID eventId = UUID.randomUUID();
        dedupePort.markAlreadySeen(eventId);

        String json = event(eventId, "ECO-DUP",
                "ECOMMERCE-STORE", "WH-MAIN", "SKU-APPLE-001");

        consumer.onMessage(json, null);

        // Dedupe short-circuits before the order-intake runs.
        assertThat(orderPersistence.orderCount()).isZero();
    }

    @Test
    void duplicateOrderNoIsIdempotentNoOp() {
        String json = event(UUID.randomUUID(), "ECO-2001",
                "ECOMMERCE-STORE", "WH-MAIN", "SKU-APPLE-001");
        consumer.onMessage(json, null);
        assertThat(orderPersistence.orderCount()).isEqualTo(1);

        // Second fulfillment for the same orderNo, fresh eventId — existsByOrderNo
        // guard surfaces OrderNoDuplicateException, which the consumer absorbs.
        String resent = event(UUID.randomUUID(), "ECO-2001",
                "ECOMMERCE-STORE", "WH-MAIN", "SKU-APPLE-001");
        consumer.onMessage(resent, null);

        assertThat(orderPersistence.orderCount()).isEqualTo(1);
    }

    @Test
    void unresolvedPartnerThrowsForDlt() {
        String json = event(UUID.randomUUID(), "ECO-3001",
                "UNKNOWN-PARTNER", "WH-MAIN", "SKU-APPLE-001");

        assertThatThrownBy(() -> consumer.onMessage(json, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partner not found");
        assertThat(orderPersistence.orderCount()).isZero();
    }

    @Test
    void unresolvedWarehouseThrowsForDlt() {
        String json = event(UUID.randomUUID(), "ECO-3002",
                "ECOMMERCE-STORE", "WH-UNKNOWN", "SKU-APPLE-001");

        assertThatThrownBy(() -> consumer.onMessage(json, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Warehouse not found");
        assertThat(orderPersistence.orderCount()).isZero();
    }

    @Test
    void unresolvedSkuThrowsForDlt() {
        String json = event(UUID.randomUUID(), "ECO-3003",
                "ECOMMERCE-STORE", "WH-MAIN", "SKU-UNKNOWN");

        assertThatThrownBy(() -> consumer.onMessage(json, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU not found");
        assertThat(orderPersistence.orderCount()).isZero();
    }

    @Test
    void inactivePartnerThrowsForDlt() {
        UUID inactiveId = UUID.randomUUID();
        masterReadModel.addPartner(inactiveId, "ECOMMERCE-INACTIVE",
                PartnerSnapshot.PartnerType.CUSTOMER, PartnerSnapshot.Status.INACTIVE);

        String json = event(UUID.randomUUID(), "ECO-3004",
                "ECOMMERCE-INACTIVE", "WH-MAIN", "SKU-APPLE-001");

        assertThatThrownBy(() -> consumer.onMessage(json, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not eligible");
        assertThat(orderPersistence.orderCount()).isZero();
    }

    /** Inner-class fake of EventDedupePort. Tracks seen ids. */
    private static class FakeEventDedupePort implements EventDedupePort {
        private final Set<UUID> seen = new HashSet<>();

        void markAlreadySeen(UUID eventId) {
            seen.add(eventId);
        }

        @Override
        public Outcome process(UUID eventId, String eventType, Runnable work) {
            if (seen.contains(eventId)) {
                return Outcome.IGNORED_DUPLICATE;
            }
            seen.add(eventId);
            work.run();
            return Outcome.APPLIED;
        }
    }
}
