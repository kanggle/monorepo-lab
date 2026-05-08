package com.wms.admin.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.admin.infra.persistence.readmodel.AdminEventDedupeJpaRepository;
import com.wms.admin.readmodel.inbound.AsnSummaryRepository;
import com.wms.admin.readmodel.inbound.InspectionSummaryRepository;
import com.wms.admin.readmodel.throughput.ThroughputDailyId;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyRepository;
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
 * Per-source-topic Kafka IT for {@code InboundProjectionConsumer}. Covers the
 * 3 inbound topics ({@code wms.inbound.asn.v1},
 * {@code wms.inbound.inspection.completed.v1},
 * {@code wms.inbound.putaway.completed.v1}) plus dedupe-hit, LWW-stale, and
 * DLT routing scenarios.
 */
class InboundProjectionKafkaIT extends ProjectionKafkaIntegrationBase {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired AdminEventDedupeJpaRepository dedupe;
    @Autowired AsnSummaryRepository asnRepo;
    @Autowired InspectionSummaryRepository inspectionRepo;
    @Autowired ThroughputInboundDailyRepository throughputRepo;
    @Autowired MeterRegistry meterRegistry;

    private static final Duration AWAIT = Duration.ofSeconds(30);

    // ----- happy paths (3 topics) -------------------------------------

    @Test
    void asnReceived_upsertsAsnSummary() {
        UUID eventId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"asnId":"%s","asnNo":"ASN-001","warehouseId":"%s",
                "source":"WEBHOOK_ERP","expectedArriveDate":"2026-05-15","lines":[]}"""
                .formatted(asnId, warehouseId);

        kafkaTemplate.send("wms.inbound.asn.v1", asnId.toString(),
                KafkaTestSupport.envelope(eventId, "inbound.asn.received", Instant.now(),
                        asnId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(dedupe.existsById(eventId)).isTrue();
            assertThat(asnRepo.findById(asnId)).isPresent()
                    .get().satisfies(a -> {
                        assertThat(a.getAsnNo()).isEqualTo("ASN-001");
                        assertThat(a.getStatus()).isEqualTo("CREATED");
                    });
        });
    }

    @Test
    void asnCancelled_setsStatus() {
        UUID warehouseId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now();

        UUID receivedId = UUID.randomUUID();
        String received = """
                {"asnId":"%s","asnNo":"ASN-CANCEL","warehouseId":"%s",
                "source":"MANUAL","lines":[]}""".formatted(asnId, warehouseId);
        kafkaTemplate.send("wms.inbound.asn.v1", asnId.toString(),
                KafkaTestSupport.envelope(receivedId, "inbound.asn.received", t1,
                        asnId.toString(), received));
        await().atMost(AWAIT).untilAsserted(() -> assertThat(asnRepo.findById(asnId)).isPresent());

        UUID cancelId = UUID.randomUUID();
        String cancel = """
                {"asnId":"%s","asnNo":"ASN-CANCEL","warehouseId":"%s"}"""
                .formatted(asnId, warehouseId);
        kafkaTemplate.send("wms.inbound.asn.v1", asnId.toString(),
                KafkaTestSupport.envelope(cancelId, "inbound.asn.cancelled", t2,
                        asnId.toString(), cancel));

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(asnRepo.findById(asnId).orElseThrow().getStatus()).isEqualTo("CANCELLED"));
    }

    @Test
    void inspectionCompleted_upsertsInspectionSummary() {
        UUID eventId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"asnId":"%s","warehouseId":"%s","inspectorId":"alice",
                "discrepancyCount":1,
                "lines":[{"expectedQty":10,"qtyPassed":8,"qtyDamaged":1,"qtyShort":1}]}"""
                .formatted(asnId, warehouseId);

        kafkaTemplate.send("wms.inbound.inspection.completed.v1", asnId.toString(),
                KafkaTestSupport.envelope(eventId, "inbound.inspection.completed", Instant.now(),
                        asnId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(inspectionRepo.findById(asnId)).isPresent()
                    .get().satisfies(i -> {
                        assertThat(i.getDiscrepancyCount()).isEqualTo(1);
                        assertThat(i.getTotalLines()).isEqualTo(1);
                        assertThat(i.getTotalQtyPassed()).isEqualTo(8);
                    });
        });
    }

    @Test
    void putawayCompleted_atomicallyIncrementsThroughputCounter() {
        UUID eventId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-05-09T13:45:00Z");
        LocalDate date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
        String payload = """
                {"warehouseId":"%s","completedAt":"%s",
                "lines":[{"qtyReceived":10},{"qtyReceived":15}]}"""
                .formatted(warehouseId, occurredAt);

        kafkaTemplate.send("wms.inbound.putaway.completed.v1", warehouseId.toString(),
                KafkaTestSupport.envelope(eventId, "inbound.putaway.completed", occurredAt,
                        warehouseId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(dedupe.existsById(eventId)).isTrue();
            assertThat(throughputRepo.findById(new ThroughputDailyId(date, warehouseId)))
                    .isPresent().get().satisfies(t -> {
                        assertThat(t.getPutawayCount()).isEqualTo(1);
                        assertThat(t.getQtyReceived()).isEqualTo(25);
                    });
        });
    }

    // ----- dedupe-hit -------------------------------------------------

    @Test
    void duplicateEventId_secondPublishIgnored() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        // Second envelope reuses the same eventId but carries a *different*
        // asnNo. If dedupe blocks the second consume, the read-model row
        // keeps the FIRST name. If dedupe somehow misses, the second event
        // would overwrite to SECOND — which the assertion catches.
        String firstEnvelope = KafkaTestSupport.envelope(eventId, "inbound.asn.received",
                occurredAt, asnId.toString(),
                """
                {"asnId":"%s","asnNo":"FIRST","warehouseId":"%s",
                "source":"MANUAL","lines":[]}""".formatted(asnId, warehouseId));
        String secondEnvelope = KafkaTestSupport.envelope(eventId, "inbound.asn.received",
                occurredAt, asnId.toString(),
                """
                {"asnId":"%s","asnNo":"SECOND","warehouseId":"%s",
                "source":"MANUAL","lines":[]}""".formatted(asnId, warehouseId));

        kafkaTemplate.send("wms.inbound.asn.v1", asnId.toString(), firstEnvelope).get();
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(asnRepo.findById(asnId)).isPresent()
                        .get().satisfies(a -> assertThat(a.getAsnNo()).isEqualTo("FIRST")));

        kafkaTemplate.send("wms.inbound.asn.v1", asnId.toString(), secondEnvelope).get();
        Thread.sleep(3_000);

        assertThat(asnRepo.findById(asnId).orElseThrow().getAsnNo())
                .as("dedupe-hit must reject the second event's payload")
                .isEqualTo("FIRST");
    }

    // ----- LWW-stale (asn_summary upsert) -----------------------------

    @Test
    void staleOccurredAt_lwwGuardSkips() {
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant fresh = Instant.now();
        Instant stale = fresh.minusSeconds(120);

        UUID firstId = UUID.randomUUID();
        String fresher = """
                {"asnId":"%s","asnNo":"FRESH","warehouseId":"%s",
                "source":"MANUAL","lines":[]}""".formatted(asnId, warehouseId);
        kafkaTemplate.send("wms.inbound.asn.v1", asnId.toString(),
                KafkaTestSupport.envelope(firstId, "inbound.asn.received", fresh,
                        asnId.toString(), fresher));
        await().atMost(AWAIT).untilAsserted(() -> assertThat(asnRepo.findById(asnId)).isPresent());

        UUID staleId = UUID.randomUUID();
        String staler = """
                {"asnId":"%s","asnNo":"STALE","warehouseId":"%s",
                "source":"MANUAL","lines":[]}""".formatted(asnId, warehouseId);
        kafkaTemplate.send("wms.inbound.asn.v1", asnId.toString(),
                KafkaTestSupport.envelope(staleId, "inbound.asn.received", stale,
                        asnId.toString(), staler));

        await().atMost(AWAIT).untilAsserted(() -> {
            // dedupe row exists for staleId — but with IGNORED_DUPLICATE_LATE outcome.
            assertThat(dedupe.existsById(staleId)).isTrue();
            // read-model row keeps the FRESH name (LWW guard skipped the stale write).
            assertThat(asnRepo.findById(asnId).orElseThrow().getAsnNo()).isEqualTo("FRESH");
        });
    }

    // ----- DLT routing -----------------------------------------------

    @Test
    void malformedEnvelope_routedToDlt() {
        String dltTopic = "wms.inbound.asn.v1.DLT";
        String malformed = """
                {"eventType":"inbound.asn.received","occurredAt":"2026-05-09T10:00:00Z",
                "aggregateId":"asn-DLT","payload":{"asnId":"asn-DLT","asnNo":"ASN-DLT"}}""";

        double errorBefore = errorCount("wms.inbound.asn.v1");
        kafkaTemplate.send("wms.inbound.asn.v1", "dlt-key", malformed);

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(errorCount("wms.inbound.asn.v1")).isGreaterThan(errorBefore));

        var dltRecords = KafkaTestSupport.pollDlt(KAFKA.getBootstrapServers(),
                dltTopic, Duration.ofSeconds(15));
        assertThat(dltRecords).as("DLT for %s", dltTopic).isNotEmpty();
        assertThat(KafkaTestSupport.valueContains(dltRecords, "ASN-DLT")).isTrue();
    }

    // ----- helpers ----------------------------------------------------

    private double errorCount(String topic) {
        var counter = meterRegistry.find("admin.projection.error.count")
                .tag("topic", topic).counter();
        return counter == null ? 0.0d : counter.count();
    }
}
