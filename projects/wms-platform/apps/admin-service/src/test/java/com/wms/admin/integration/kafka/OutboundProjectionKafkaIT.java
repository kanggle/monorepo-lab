package com.wms.admin.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.admin.infra.persistence.readmodel.AdminEventDedupeJpaRepository;
import com.wms.admin.readmodel.outbound.OrderSummaryRepository;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import com.wms.admin.readmodel.throughput.ThroughputDailyId;
import com.wms.admin.readmodel.throughput.ThroughputOutboundDailyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Per-source-topic Kafka IT for {@code OutboundProjectionConsumer}. Covers the
 * 2 outbound topics ({@code wms.outbound.order.v1},
 * {@code wms.outbound.shipping.confirmed.v1}) plus dedupe-hit, LWW-stale, and
 * DLT routing scenarios.
 */
class OutboundProjectionKafkaIT extends ProjectionKafkaIntegrationBase {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired AdminEventDedupeJpaRepository dedupe;
    @Autowired OrderSummaryRepository orderRepo;
    @Autowired ShipmentSummaryRepository shipmentRepo;
    @Autowired ThroughputOutboundDailyRepository throughputRepo;
    @Autowired MeterRegistry meterRegistry;

    private static final Duration AWAIT = Duration.ofSeconds(30);

    @Test
    void orderReceived_upsertsOrderSummary() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"orderId":"%s","orderNo":"ORD-1","warehouseId":"%s",
                "source":"WEBHOOK_ERP","requiredShipDate":"2026-05-15","lines":[{}]}"""
                .formatted(orderId, warehouseId);

        kafkaTemplate.send("wms.outbound.order.v1", orderId.toString(),
                KafkaTestSupport.envelope(eventId, "outbound.order.received", Instant.now(),
                        orderId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(dedupe.existsById(eventId)).isTrue();
            assertThat(orderRepo.findById(orderId)).isPresent()
                    .get().satisfies(o -> {
                        assertThat(o.getOrderNo()).isEqualTo("ORD-1");
                        assertThat(o.getStatus()).isEqualTo("RECEIVED");
                    });
        });
    }

    @Test
    void shippingConfirmed_appendsShipmentAndIncrementsThroughput() {
        UUID eventId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-05-09T15:00:00Z");
        LocalDate date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
        String payload = """
                {"shipmentId":"%s","orderId":"%s","warehouseId":"%s",
                "shippedAt":"%s","carrierCode":"DHL",
                "lines":[{"qtyConfirmed":100},{"qtyConfirmed":50}]}"""
                .formatted(shipmentId, orderId, warehouseId, occurredAt);

        kafkaTemplate.send("wms.outbound.shipping.confirmed.v1", shipmentId.toString(),
                KafkaTestSupport.envelope(eventId, "outbound.shipping.confirmed", occurredAt,
                        shipmentId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(shipmentRepo.findById(shipmentId)).isPresent()
                    .get().satisfies(s -> assertThat(s.getCarrierCode()).isEqualTo("DHL"));
            assertThat(throughputRepo.findById(new ThroughputDailyId(date, warehouseId)))
                    .isPresent().get().satisfies(t -> {
                        assertThat(t.getShipmentCount()).isEqualTo(1);
                        assertThat(t.getQtyShipped()).isEqualTo(150);
                    });
        });
    }

    // ----- dedupe-hit -------------------------------------------------

    @Test
    void duplicateEventId_secondPublishIgnored() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String first = KafkaTestSupport.envelope(eventId, "outbound.order.received",
                occurredAt, orderId.toString(),
                """
                {"orderId":"%s","orderNo":"FIRST","warehouseId":"%s","lines":[]}"""
                        .formatted(orderId, warehouseId));
        String second = KafkaTestSupport.envelope(eventId, "outbound.order.received",
                occurredAt, orderId.toString(),
                """
                {"orderId":"%s","orderNo":"SECOND","warehouseId":"%s","lines":[]}"""
                        .formatted(orderId, warehouseId));

        kafkaTemplate.send("wms.outbound.order.v1", orderId.toString(), first).get();
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(orderRepo.findById(orderId)).isPresent()
                        .get().satisfies(o -> assertThat(o.getOrderNo()).isEqualTo("FIRST")));

        kafkaTemplate.send("wms.outbound.order.v1", orderId.toString(), second).get();
        Thread.sleep(3_000);

        assertThat(orderRepo.findById(orderId).orElseThrow().getOrderNo())
                .as("dedupe-hit must reject the second event's payload")
                .isEqualTo("FIRST");
    }

    // ----- LWW-stale --------------------------------------------------

    @Test
    void staleOccurredAt_lwwGuardSkips() {
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant fresh = Instant.now();
        Instant stale = fresh.minusSeconds(120);

        UUID firstId = UUID.randomUUID();
        kafkaTemplate.send("wms.outbound.order.v1", orderId.toString(),
                KafkaTestSupport.envelope(firstId, "outbound.order.received", fresh,
                        orderId.toString(),
                        """
                        {"orderId":"%s","orderNo":"FRESH","warehouseId":"%s","lines":[]}"""
                                .formatted(orderId, warehouseId)));
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(orderRepo.findById(orderId)).isPresent());

        UUID staleId = UUID.randomUUID();
        kafkaTemplate.send("wms.outbound.order.v1", orderId.toString(),
                KafkaTestSupport.envelope(staleId, "outbound.order.received", stale,
                        orderId.toString(),
                        """
                        {"orderId":"%s","orderNo":"STALE","warehouseId":"%s","lines":[]}"""
                                .formatted(orderId, warehouseId)));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(dedupe.existsById(staleId)).isTrue();
            assertThat(orderRepo.findById(orderId).orElseThrow().getOrderNo()).isEqualTo("FRESH");
        });
    }

    // ----- DLT routing -----------------------------------------------

    @Test
    void unknownEventType_routedToDlt() {
        String dltTopic = "wms.outbound.order.v1.DLT";
        UUID orderId = UUID.randomUUID();
        // Unknown eventType — UnknownEventTypeException is in
        // ProjectionKafkaConsumerConfig's non-retryable list → immediate DLT.
        String envelope = KafkaTestSupport.envelope(UUID.randomUUID(),
                "outbound.order.never_seen_event", Instant.now(),
                orderId.toString(),
                """
                {"orderId":"%s","orderNo":"DLT-UNKNOWN","warehouseId":"%s","lines":[]}"""
                        .formatted(orderId, UUID.randomUUID()));

        double errorBefore = errorCount("wms.outbound.order.v1");
        kafkaTemplate.send("wms.outbound.order.v1", orderId.toString(), envelope);

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(errorCount("wms.outbound.order.v1")).isGreaterThan(errorBefore));

        var dltRecords = KafkaTestSupport.pollDlt(KAFKA.getBootstrapServers(),
                dltTopic, Duration.ofSeconds(15));
        assertThat(dltRecords).as("DLT for %s", dltTopic).isNotEmpty();
        assertThat(KafkaTestSupport.valueContains(dltRecords, "DLT-UNKNOWN")).isTrue();
    }

    private double errorCount(String topic) {
        var counter = meterRegistry.find("admin.projection.error.count")
                .tag("topic", topic).counter();
        return counter == null ? 0.0d : counter.count();
    }
}
