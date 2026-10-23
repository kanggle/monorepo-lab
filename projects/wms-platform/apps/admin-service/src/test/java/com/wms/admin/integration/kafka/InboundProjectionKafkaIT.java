package com.wms.admin.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.admin.infra.persistence.readmodel.AdminEventDedupeJpaRepository;
import com.wms.admin.readmodel.inbound.AsnSummaryRepository;
import com.wms.admin.readmodel.inbound.InspectionSummaryRepository;
import com.wms.admin.readmodel.throughput.PutawayFactRepository;
import com.wms.admin.readmodel.throughput.ThroughputDailyId;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import com.example.common.time.KstPeriodBounds;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Per-source-topic Kafka IT for {@code InboundProjectionConsumer}. Each event
 * type is produced onto the topic {@code inbound-service} really publishes it
 * to ({@code inbound-events.md § Topic Layout}) — before {@code TASK-BE-582}
 * these sends used the rolled-up {@code wms.inbound.asn.v1}, a name no
 * producer writes to, so the suite was green while production projected
 * nothing. Covers the ASN / inspection / putaway topics plus dedupe-hit,
 * LWW-stale, and DLT routing scenarios.
 */
class InboundProjectionKafkaIT extends ProjectionKafkaIntegrationBase {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired AdminEventDedupeJpaRepository dedupe;
    @Autowired AsnSummaryRepository asnRepo;
    @Autowired InspectionSummaryRepository inspectionRepo;
    @Autowired ThroughputInboundDailyRepository throughputRepo;
    @Autowired PutawayFactRepository putawayFactRepo;
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

        kafkaTemplate.send("wms.inbound.asn.received.v1", asnId.toString(),
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
        kafkaTemplate.send("wms.inbound.asn.received.v1", asnId.toString(),
                KafkaTestSupport.envelope(receivedId, "inbound.asn.received", t1,
                        asnId.toString(), received));
        await().atMost(AWAIT).untilAsserted(() -> assertThat(asnRepo.findById(asnId)).isPresent());

        UUID cancelId = UUID.randomUUID();
        String cancel = """
                {"asnId":"%s","asnNo":"ASN-CANCEL","warehouseId":"%s"}"""
                .formatted(asnId, warehouseId);
        kafkaTemplate.send("wms.inbound.asn.cancelled.v1", asnId.toString(),
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
        LocalDate date = KstPeriodBounds.dateOf(occurredAt); // TASK-MONO-808 — the KST day
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
            // TASK-MONO-808 — the per-event row, in the same transaction (V6)
            assertThat(putawayFactRepo.findById(eventId)).isPresent().get().satisfies(f -> {
                assertThat(f.getWarehouseId()).isEqualTo(warehouseId);
                assertThat(f.getCompletedAt()).isEqualTo(occurredAt);
                assertThat(f.getQtyReceived()).isEqualTo(25);
            });
        });
    }

    // ----- TASK-BE-643: counters are not LWW ---------------------------
    // Two putaways of *different* ASNs land on the same (date, warehouse) row.
    // They are keyed by asnId, so arrival order is not occurredAt order
    // (inbound-events.md:400). Sent latest-first on purpose — the earlier suite
    // only ever sent in occurredAt order and so could not see the drop.

    @Test
    void putawayCompleted_reverseOrderAcrossAsns_countsBoth() {
        UUID warehouseId = UUID.randomUUID();
        Instant t2 = Instant.parse("2026-05-10T15:00:00Z");
        Instant t1 = Instant.parse("2026-05-10T09:00:00Z");
        LocalDate date = LocalDate.of(2026, 5, 10);
        UUID laterId = UUID.randomUUID();
        UUID earlierId = UUID.randomUUID();
        UUID asnLater = UUID.randomUUID();
        UUID asnEarlier = UUID.randomUUID();

        kafkaTemplate.send("wms.inbound.putaway.completed.v1", asnLater.toString(),
                KafkaTestSupport.envelope(laterId, "inbound.putaway.completed", t2,
                        asnLater.toString(), putawayPayload(warehouseId, t2, 10)));
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(dedupe.existsById(laterId)).isTrue());

        kafkaTemplate.send("wms.inbound.putaway.completed.v1", asnEarlier.toString(),
                KafkaTestSupport.envelope(earlierId, "inbound.putaway.completed", t1,
                        asnEarlier.toString(), putawayPayload(warehouseId, t1, 15)));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(dedupe.findById(earlierId)).isPresent().get()
                    .satisfies(d -> assertThat(d.getOutcome()).isEqualTo("APPLIED"));
            assertThat(throughputRepo.findById(new ThroughputDailyId(date, warehouseId)))
                    .isPresent().get().satisfies(t -> {
                        assertThat(t.getPutawayCount()).isEqualTo(2);
                        assertThat(t.getQtyReceived()).isEqualTo(25);
                    });
        });
    }

    @Test
    void putawayCompleted_sameEventIdTwice_countsOnce() throws Exception {
        // 대조군 — 조건을 지운 뒤에도 같은 event_id 의 재전달은 dedupe 가 막는다.
        UUID warehouseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        Instant at = Instant.parse("2026-05-11T10:00:00Z");
        LocalDate date = LocalDate.of(2026, 5, 11);
        String env = KafkaTestSupport.envelope(eventId, "inbound.putaway.completed", at,
                asnId.toString(), putawayPayload(warehouseId, at, 7));

        kafkaTemplate.send("wms.inbound.putaway.completed.v1", asnId.toString(), env).get();
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(throughputRepo.findById(new ThroughputDailyId(date, warehouseId)))
                        .isPresent());

        kafkaTemplate.send("wms.inbound.putaway.completed.v1", asnId.toString(), env).get();
        Thread.sleep(3_000);

        var row = throughputRepo.findById(new ThroughputDailyId(date, warehouseId)).orElseThrow();
        assertThat(row.getPutawayCount()).as("redelivery must not double-count").isEqualTo(1);
        assertThat(row.getQtyReceived()).isEqualTo(7);
    }

    private static String putawayPayload(UUID warehouseId, Instant completedAt, int qty) {
        return """
                {"warehouseId":"%s","completedAt":"%s","lines":[{"qtyReceived":%d}]}"""
                .formatted(warehouseId, completedAt, qty);
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

        kafkaTemplate.send("wms.inbound.asn.received.v1", asnId.toString(), firstEnvelope).get();
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(asnRepo.findById(asnId)).isPresent()
                        .get().satisfies(a -> assertThat(a.getAsnNo()).isEqualTo("FIRST")));

        kafkaTemplate.send("wms.inbound.asn.received.v1", asnId.toString(), secondEnvelope).get();
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
        kafkaTemplate.send("wms.inbound.asn.received.v1", asnId.toString(),
                KafkaTestSupport.envelope(firstId, "inbound.asn.received", fresh,
                        asnId.toString(), fresher));
        await().atMost(AWAIT).untilAsserted(() -> assertThat(asnRepo.findById(asnId)).isPresent());

        UUID staleId = UUID.randomUUID();
        String staler = """
                {"asnId":"%s","asnNo":"STALE","warehouseId":"%s",
                "source":"MANUAL","lines":[]}""".formatted(asnId, warehouseId);
        kafkaTemplate.send("wms.inbound.asn.received.v1", asnId.toString(),
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
        String dltTopic = "wms.inbound.asn.received.v1.DLT";
        String malformed = """
                {"eventType":"inbound.asn.received","occurredAt":"2026-05-09T10:00:00Z",
                "aggregateId":"asn-DLT","payload":{"asnId":"asn-DLT","asnNo":"ASN-DLT"}}""";

        double errorBefore = errorCount("wms.inbound.asn.received.v1");
        kafkaTemplate.send("wms.inbound.asn.received.v1", "dlt-key", malformed);

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(errorCount("wms.inbound.asn.received.v1")).isGreaterThan(errorBefore));

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
