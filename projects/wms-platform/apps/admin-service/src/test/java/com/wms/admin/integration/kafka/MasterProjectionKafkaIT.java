package com.wms.admin.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.admin.infra.persistence.readmodel.AdminEventDedupeJpaRepository;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.SkuRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.master.ZoneRefRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Per-source-topic Kafka IT for {@code MasterProjectionConsumer}. Covers the
 * 6 master topics ({@code wms.master.{warehouse|zone|location|sku|partner|lot}.v1})
 * plus dedupe-hit, LWW-stale, and DLT routing scenarios.
 */
class MasterProjectionKafkaIT extends ProjectionKafkaIntegrationBase {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired AdminEventDedupeJpaRepository dedupe;
    @Autowired WarehouseRefRepository warehouseRepo;
    @Autowired ZoneRefRepository zoneRepo;
    @Autowired LocationRefRepository locationRepo;
    @Autowired SkuRefRepository skuRepo;
    @Autowired LotRefRepository lotRepo;
    @Autowired PartnerRefRepository partnerRepo;
    @Autowired MeterRegistry meterRegistry;

    private static final Duration AWAIT = Duration.ofSeconds(30);

    // ----- happy paths (6 topics) -------------------------------------

    @Test
    void warehouseCreated_upsertsWarehouseRef() {
        UUID eventId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"warehouse":{"id":"%s","warehouseCode":"WH01","name":"Seoul",
                "timezone":"Asia/Seoul","status":"ACTIVE"}}""".formatted(warehouseId);

        kafkaTemplate.send("wms.master.warehouse.v1", warehouseId.toString(),
                KafkaTestSupport.envelope(eventId, "master.warehouse.created", Instant.now(),
                        warehouseId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(dedupe.existsById(eventId)).isTrue();
            assertThat(warehouseRepo.findById(warehouseId)).isPresent()
                    .get().satisfies(w -> assertThat(w.getWarehouseCode()).isEqualTo("WH01"));
        });
    }

    @Test
    void zoneCreated_upsertsZoneRef() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"zone":{"id":"%s","warehouseId":"%s","zoneCode":"Z01","name":"Receiving",
                "zoneType":"RECEIVING","status":"ACTIVE"}}""".formatted(zoneId, warehouseId);

        kafkaTemplate.send("wms.master.zone.v1", zoneId.toString(),
                KafkaTestSupport.envelope(eventId, "master.zone.created", Instant.now(),
                        zoneId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(zoneRepo.findById(zoneId)).isPresent()
                    .get().satisfies(z -> assertThat(z.getZoneCode()).isEqualTo("Z01"));
        });
    }

    @Test
    void locationCreated_upsertsLocationRef() {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"location":{"id":"%s","locationCode":"WH01-A-01-01-01","warehouseId":"%s",
                "locationType":"BIN","status":"ACTIVE"}}""".formatted(locationId, warehouseId);

        kafkaTemplate.send("wms.master.location.v1", locationId.toString(),
                KafkaTestSupport.envelope(eventId, "master.location.created", Instant.now(),
                        locationId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(locationRepo.findById(locationId)).isPresent()
                    .get().satisfies(l ->
                            assertThat(l.getLocationCode()).isEqualTo("WH01-A-01-01-01"));
        });
    }

    @Test
    void skuCreated_upsertsSkuRef() {
        UUID eventId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        String payload = """
                {"sku":{"id":"%s","skuCode":"SKU-001","name":"Apple",
                "baseUom":"EA","trackingType":"LOT","status":"ACTIVE"}}""".formatted(skuId);

        kafkaTemplate.send("wms.master.sku.v1", skuId.toString(),
                KafkaTestSupport.envelope(eventId, "master.sku.created", Instant.now(),
                        skuId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(skuRepo.findById(skuId)).isPresent()
                    .get().satisfies(s -> assertThat(s.getSkuCode()).isEqualTo("SKU-001"));
        });
    }

    @Test
    void partnerCreated_upsertsPartnerRef() {
        UUID eventId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        String payload = """
                {"partner":{"id":"%s","partnerCode":"SUP-001","name":"AcmeCo",
                "partnerType":"SUPPLIER","status":"ACTIVE"}}""".formatted(partnerId);

        kafkaTemplate.send("wms.master.partner.v1", partnerId.toString(),
                KafkaTestSupport.envelope(eventId, "master.partner.created", Instant.now(),
                        partnerId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(partnerRepo.findById(partnerId)).isPresent()
                    .get().satisfies(p -> assertThat(p.getPartnerCode()).isEqualTo("SUP-001"));
        });
    }

    @Test
    void lotCreated_upsertsLotRef() {
        UUID eventId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        String payload = """
                {"lot":{"id":"%s","skuId":"%s","lotNo":"L-2026-A",
                "expiryDate":"2027-01-01","status":"ACTIVE"}}""".formatted(lotId, skuId);

        kafkaTemplate.send("wms.master.lot.v1", lotId.toString(),
                KafkaTestSupport.envelope(eventId, "master.lot.created", Instant.now(),
                        lotId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(lotRepo.findById(lotId)).isPresent()
                    .get().satisfies(l -> assertThat(l.getLotNo()).isEqualTo("L-2026-A"));
        });
    }

    // ----- dedupe-hit -------------------------------------------------

    @Test
    void duplicateEventId_secondPublishIgnored() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        // Second envelope reuses the same eventId but carries a *different*
        // warehouse name. If dedupe blocks, read-model row keeps FIRST_NAME.
        String first = KafkaTestSupport.envelope(eventId, "master.warehouse.created",
                occurredAt, warehouseId.toString(),
                """
                {"warehouse":{"id":"%s","warehouseCode":"WH-DEDUPE","name":"FIRST_NAME",
                "status":"ACTIVE"}}""".formatted(warehouseId));
        String second = KafkaTestSupport.envelope(eventId, "master.warehouse.created",
                occurredAt, warehouseId.toString(),
                """
                {"warehouse":{"id":"%s","warehouseCode":"WH-DEDUPE","name":"SECOND_NAME",
                "status":"ACTIVE"}}""".formatted(warehouseId));

        kafkaTemplate.send("wms.master.warehouse.v1", warehouseId.toString(), first).get();
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(warehouseRepo.findById(warehouseId)).isPresent()
                        .get().satisfies(w -> assertThat(w.getName()).isEqualTo("FIRST_NAME")));

        kafkaTemplate.send("wms.master.warehouse.v1", warehouseId.toString(), second).get();
        Thread.sleep(3_000);

        assertThat(warehouseRepo.findById(warehouseId).orElseThrow().getName())
                .as("dedupe-hit must reject the second event's payload")
                .isEqualTo("FIRST_NAME");
        assertThat(dedupe.existsById(eventId)).isTrue();
    }

    // ----- LWW-stale --------------------------------------------------

    @Test
    void staleOccurredAt_secondEventIgnored() {
        UUID warehouseId = UUID.randomUUID();
        Instant fresh = Instant.now();
        Instant stale = fresh.minusSeconds(120);

        UUID firstEventId = UUID.randomUUID();
        String freshPayload = """
                {"warehouse":{"id":"%s","warehouseCode":"WH-LWW","name":"FreshName",
                "status":"ACTIVE"}}""".formatted(warehouseId);
        kafkaTemplate.send("wms.master.warehouse.v1", warehouseId.toString(),
                KafkaTestSupport.envelope(firstEventId, "master.warehouse.created", fresh,
                        warehouseId.toString(), freshPayload));
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(warehouseRepo.findById(warehouseId)).isPresent());

        UUID staleEventId = UUID.randomUUID();
        double droppedStaleBefore = droppedCount("stale");
        String stalePayload = """
                {"warehouse":{"id":"%s","warehouseCode":"WH-LWW","name":"StaleName",
                "status":"ACTIVE"}}""".formatted(warehouseId);
        kafkaTemplate.send("wms.master.warehouse.v1", warehouseId.toString(),
                KafkaTestSupport.envelope(staleEventId, "master.warehouse.updated", stale,
                        warehouseId.toString(), stalePayload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(droppedCount("stale")).isGreaterThan(droppedStaleBefore);
            assertThat(warehouseRepo.findById(warehouseId).orElseThrow().getName())
                    .isEqualTo("FreshName");
        });
    }

    // ----- DLT routing (non-retryable) ---------------------------------

    @Test
    void malformedEnvelope_routedToDlt() {
        String dltTopic = "wms.master.warehouse.v1.DLT";
        UUID warehouseId = UUID.randomUUID();
        // Missing required eventId field — envelope parser throws
        // IllegalArgumentException → non-retryable list → DLT.
        String malformed = """
                {"eventType":"master.warehouse.created","occurredAt":"2026-05-09T10:00:00Z",
                "aggregateId":"%s",
                "payload":{"warehouse":{"id":"%s","warehouseCode":"WH-DLT","name":"D",
                "status":"ACTIVE"}}}""".formatted(warehouseId, warehouseId);

        double errorCountBefore = errorCount("wms.master.warehouse.v1");
        kafkaTemplate.send("wms.master.warehouse.v1", warehouseId.toString(), malformed);

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(errorCount("wms.master.warehouse.v1")).isGreaterThan(errorCountBefore));

        var dltRecords = KafkaTestSupport.pollDlt(KAFKA.getBootstrapServers(),
                dltTopic, Duration.ofSeconds(15));
        assertThat(dltRecords).as("DLT for %s", dltTopic).isNotEmpty();
        assertThat(KafkaTestSupport.valueContains(dltRecords, "WH-DLT")).isTrue();

        assertThat(warehouseRepo.findById(warehouseId)).as("read-model untouched").isEmpty();
    }

    // ----- helpers ----------------------------------------------------

    private double droppedCount(String reason) {
        var counter = meterRegistry.find("admin.projection.dropped.count")
                .tag("reason", reason).counter();
        return counter == null ? 0.0d : counter.count();
    }

    private double errorCount(String topic) {
        var counter = meterRegistry.find("admin.projection.error.count")
                .tag("topic", topic).counter();
        return counter == null ? 0.0d : counter.count();
    }
}
