package com.wms.admin.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.admin.infra.persistence.readmodel.AdminEventDedupeJpaRepository;
import com.wms.admin.readmodel.alert.AlertLogRepository;
import com.wms.admin.readmodel.inventory.AdjustmentAuditRepository;
import com.wms.admin.readmodel.inventory.InventorySnapshotId;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Per-source-topic Kafka IT for {@code InventoryProjectionConsumer}. Covers
 * the 7 inventory topics ({@code wms.inventory.{received|adjusted|transferred|
 * reserved|released|confirmed|alert}.v1}) plus dedupe-hit, LWW-stale, DLT
 * routing, and the read-model rebuild replay scenario.
 */
class InventoryProjectionKafkaIT extends ProjectionKafkaIntegrationBase {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired AdminEventDedupeJpaRepository dedupe;
    @Autowired InventorySnapshotRepository snapshotRepo;
    @Autowired AdjustmentAuditRepository auditRepo;
    @Autowired AlertLogRepository alertRepo;
    @Autowired MeterRegistry meterRegistry;

    private static final Duration AWAIT = Duration.ofSeconds(30);

    // ----- happy paths (7 topics) -------------------------------------

    @Test
    void inventoryReceived_upsertsSnapshot() {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"warehouseId":"%s","lines":[
                    {"locationId":"%s","skuId":"%s","availableQtyAfter":50,"reservedQtyAfter":0}
                ]}""".formatted(warehouseId, locationId, skuId);

        kafkaTemplate.send("wms.inventory.received.v1", locationId.toString(),
                KafkaTestSupport.envelope(eventId, "inventory.received", Instant.now(),
                        locationId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null)))
                        .isPresent().get().satisfies(s ->
                                assertThat(s.getAvailableQty()).isEqualTo(50)));
    }

    @Test
    void inventoryAdjusted_upsertsSnapshotAndAppendsAudit() {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"locationId":"%s","skuId":"%s","warehouseId":"%s","bucket":"AVAILABLE",
                "delta":-5,"reasonCode":"LOSS","reasonNote":"lost","actorId":"alice",
                "inventory":{"availableQty":45,"reservedQty":0,"damagedQty":0}}"""
                .formatted(locationId, skuId, warehouseId);

        kafkaTemplate.send("wms.inventory.adjusted.v1", locationId.toString(),
                KafkaTestSupport.envelope(eventId, "inventory.adjusted", Instant.now(),
                        locationId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(auditRepo.existsById(eventId)).isTrue();
            assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null)))
                    .isPresent().get().satisfies(s ->
                            assertThat(s.getAvailableQty()).isEqualTo(45));
        });
    }

    @Test
    void inventoryTransferred_dualWritesSourceAndTarget() {
        UUID eventId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID sourceLoc = UUID.randomUUID();
        UUID targetLoc = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"warehouseId":"%s","skuId":"%s",
                "source":{"locationId":"%s","availableQtyAfter":40},
                "target":{"locationId":"%s","availableQtyAfter":10}}"""
                .formatted(warehouseId, skuId, sourceLoc, targetLoc);

        kafkaTemplate.send("wms.inventory.transferred.v1", skuId.toString(),
                KafkaTestSupport.envelope(eventId, "inventory.transferred", Instant.now(),
                        skuId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(snapshotRepo.findById(new InventorySnapshotId(sourceLoc, skuId, null)))
                    .isPresent().get().satisfies(s ->
                            assertThat(s.getAvailableQty()).isEqualTo(40));
            assertThat(snapshotRepo.findById(new InventorySnapshotId(targetLoc, skuId, null)))
                    .isPresent().get().satisfies(s ->
                            assertThat(s.getAvailableQty()).isEqualTo(10));
        });
    }

    @Test
    void inventoryReserved_increasesReservedQty() {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"warehouseId":"%s","lines":[
                    {"locationId":"%s","skuId":"%s","availableQtyAfter":40,"reservedQtyAfter":10}
                ]}""".formatted(warehouseId, locationId, skuId);

        kafkaTemplate.send("wms.inventory.reserved.v1", locationId.toString(),
                KafkaTestSupport.envelope(eventId, "inventory.reserved", Instant.now(),
                        locationId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null)))
                        .isPresent().get().satisfies(s ->
                                assertThat(s.getReservedQty()).isEqualTo(10)));
    }

    @Test
    void inventoryReleased_decreasesReservedQty() {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"warehouseId":"%s","lines":[
                    {"locationId":"%s","skuId":"%s","availableQtyAfter":50,"reservedQtyAfter":0}
                ]}""".formatted(warehouseId, locationId, skuId);

        kafkaTemplate.send("wms.inventory.released.v1", locationId.toString(),
                KafkaTestSupport.envelope(eventId, "inventory.released", Instant.now(),
                        locationId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null)))
                        .isPresent().get().satisfies(s ->
                                assertThat(s.getReservedQty()).isEqualTo(0)));
    }

    @Test
    void inventoryConfirmed_decreasesReservedQty() {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"warehouseId":"%s","lines":[
                    {"locationId":"%s","skuId":"%s","availableQtyAfter":35,"reservedQtyAfter":0}
                ]}""".formatted(warehouseId, locationId, skuId);

        kafkaTemplate.send("wms.inventory.confirmed.v1", locationId.toString(),
                KafkaTestSupport.envelope(eventId, "inventory.confirmed", Instant.now(),
                        locationId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null)))
                        .isPresent().get().satisfies(s ->
                                assertThat(s.getAvailableQty()).isEqualTo(35)));
    }

    @Test
    void inventoryLowStockDetected_appendsAlert() {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String payload = """
                {"warehouseId":"%s","locationId":"%s","skuId":"%s",
                "threshold":10,"availableQty":5}"""
                .formatted(warehouseId, locationId, skuId);

        kafkaTemplate.send("wms.inventory.alert.v1", skuId.toString(),
                KafkaTestSupport.envelope(eventId, "inventory.low-stock-detected",
                        Instant.now(), skuId.toString(), payload));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(alertRepo.existsById(eventId)).isTrue();
            assertThat(alertRepo.findById(eventId).orElseThrow().getAlertType())
                    .isEqualTo("LOW_STOCK");
        });
    }

    // ----- dedupe-hit -------------------------------------------------

    @Test
    void duplicateEventId_secondPublishIgnored() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        String first = KafkaTestSupport.envelope(eventId, "inventory.received",
                occurredAt, locationId.toString(),
                """
                {"warehouseId":"%s","lines":[
                    {"locationId":"%s","skuId":"%s","availableQtyAfter":100,"reservedQtyAfter":0}
                ]}""".formatted(warehouseId, locationId, skuId));
        String second = KafkaTestSupport.envelope(eventId, "inventory.received",
                occurredAt, locationId.toString(),
                """
                {"warehouseId":"%s","lines":[
                    {"locationId":"%s","skuId":"%s","availableQtyAfter":999,"reservedQtyAfter":0}
                ]}""".formatted(warehouseId, locationId, skuId));

        kafkaTemplate.send("wms.inventory.received.v1", locationId.toString(), first).get();
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null)))
                        .isPresent().get().satisfies(s ->
                                assertThat(s.getAvailableQty()).isEqualTo(100)));

        kafkaTemplate.send("wms.inventory.received.v1", locationId.toString(), second).get();
        Thread.sleep(3_000);

        assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null))
                .orElseThrow().getAvailableQty())
                .as("dedupe-hit must reject the second event's payload")
                .isEqualTo(100);
    }

    // ----- LWW-stale --------------------------------------------------

    @Test
    void staleOccurredAt_lwwGuardSkips() {
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant fresh = Instant.now();
        Instant stale = fresh.minusSeconds(120);

        UUID firstId = UUID.randomUUID();
        kafkaTemplate.send("wms.inventory.received.v1", locationId.toString(),
                KafkaTestSupport.envelope(firstId, "inventory.received", fresh,
                        locationId.toString(),
                        """
                        {"warehouseId":"%s","lines":[{"locationId":"%s","skuId":"%s",
                        "availableQtyAfter":100,"reservedQtyAfter":0}]}"""
                                .formatted(warehouseId, locationId, skuId)));
        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null)))
                        .isPresent());

        UUID staleId = UUID.randomUUID();
        kafkaTemplate.send("wms.inventory.received.v1", locationId.toString(),
                KafkaTestSupport.envelope(staleId, "inventory.received", stale,
                        locationId.toString(),
                        """
                        {"warehouseId":"%s","lines":[{"locationId":"%s","skuId":"%s",
                        "availableQtyAfter":777,"reservedQtyAfter":0}]}"""
                                .formatted(warehouseId, locationId, skuId)));

        await().atMost(AWAIT).untilAsserted(() -> {
            assertThat(dedupe.existsById(staleId)).isTrue();
            assertThat(snapshotRepo.findById(new InventorySnapshotId(locationId, skuId, null))
                    .orElseThrow().getAvailableQty()).isEqualTo(100);
        });
    }

    // ----- DLT routing -----------------------------------------------

    @Test
    void unknownEventType_routedToDlt() {
        String dltTopic = "wms.inventory.adjusted.v1.DLT";
        UUID locationId = UUID.randomUUID();
        String envelope = KafkaTestSupport.envelope(UUID.randomUUID(),
                "inventory.gone_rogue", Instant.now(),
                locationId.toString(),
                """
                {"locationId":"%s","skuId":"%s","warehouseId":"%s","bucket":"AVAILABLE",
                "delta":1,"inventory":{"availableQty":1,"reservedQty":0,"damagedQty":0}}"""
                        .formatted(locationId, UUID.randomUUID(), UUID.randomUUID()));

        double errorBefore = errorCount("wms.inventory.adjusted.v1");
        kafkaTemplate.send("wms.inventory.adjusted.v1", locationId.toString(), envelope);

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(errorCount("wms.inventory.adjusted.v1")).isGreaterThan(errorBefore));

        var dltRecords = KafkaTestSupport.pollDlt(KAFKA.getBootstrapServers(),
                dltTopic, Duration.ofSeconds(15));
        assertThat(dltRecords).as("DLT for %s", dltTopic).isNotEmpty();
        assertThat(KafkaTestSupport.valueContains(dltRecords, "inventory.gone_rogue")).isTrue();
    }

    // ----- Replay test -------------------------------------------------

    /**
     * {@code runbooks/read-model-rebuild.md § Step 6} verification: consume a
     * deterministic event sequence, snapshot the read-model, truncate, then
     * replay with fresh eventIds and verify the read-model converges to the
     * same business state. Uses fresh eventIds because the dedupe table is
     * also truncated; the rebuild procedure produces an equivalent snapshot
     * even though the dedupe rows are different.
     */
    @Test
    void replayProducesIdenticalReadModelState() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        InventorySnapshotId snapshotId = new InventorySnapshotId(locationId, skuId, null);

        // Sequence: received(100) → reserved(80, +20) → released(85, +15)
        kafkaTemplate.send("wms.inventory.received.v1", locationId.toString(),
                KafkaTestSupport.envelope(UUID.randomUUID(), "inventory.received",
                        Instant.now().minusSeconds(180), locationId.toString(),
                        """
                        {"warehouseId":"%s","lines":[{"locationId":"%s","skuId":"%s",
                        "availableQtyAfter":100,"reservedQtyAfter":0}]}"""
                                .formatted(warehouseId, locationId, skuId)));
        kafkaTemplate.send("wms.inventory.reserved.v1", locationId.toString(),
                KafkaTestSupport.envelope(UUID.randomUUID(), "inventory.reserved",
                        Instant.now().minusSeconds(120), locationId.toString(),
                        """
                        {"warehouseId":"%s","lines":[{"locationId":"%s","skuId":"%s",
                        "availableQtyAfter":80,"reservedQtyAfter":20}]}"""
                                .formatted(warehouseId, locationId, skuId)));
        kafkaTemplate.send("wms.inventory.released.v1", locationId.toString(),
                KafkaTestSupport.envelope(UUID.randomUUID(), "inventory.released",
                        Instant.now().minusSeconds(60), locationId.toString(),
                        """
                        {"warehouseId":"%s","lines":[{"locationId":"%s","skuId":"%s",
                        "availableQtyAfter":85,"reservedQtyAfter":15}]}"""
                                .formatted(warehouseId, locationId, skuId)));

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(snapshotRepo.findById(snapshotId)).isPresent()
                        .get().satisfies(s -> {
                            assertThat(s.getAvailableQty()).isEqualTo(85);
                            assertThat(s.getReservedQty()).isEqualTo(15);
                        }));

        var s1 = snapshotRepo.findById(snapshotId).orElseThrow();
        int s1Available = s1.getAvailableQty();
        int s1Reserved = s1.getReservedQty();

        // Truncate-equivalent: clear the read-model row + dedupe rows for this
        // location/sku via repository delete (Flyway-truncate would be more
        // realistic but @Transactional truncate inside this slice is intrusive).
        snapshotRepo.deleteById(snapshotId);

        // Replay the same sequence with FRESH eventIds (dedupe wouldn't catch
        // them because we cleared the dedupe rows in production rebuild
        // procedure; here we use new ids to simulate the same).
        kafkaTemplate.send("wms.inventory.received.v1", locationId.toString(),
                KafkaTestSupport.envelope(UUID.randomUUID(), "inventory.received",
                        Instant.now().minusSeconds(180), locationId.toString(),
                        """
                        {"warehouseId":"%s","lines":[{"locationId":"%s","skuId":"%s",
                        "availableQtyAfter":100,"reservedQtyAfter":0}]}"""
                                .formatted(warehouseId, locationId, skuId)));
        kafkaTemplate.send("wms.inventory.reserved.v1", locationId.toString(),
                KafkaTestSupport.envelope(UUID.randomUUID(), "inventory.reserved",
                        Instant.now().minusSeconds(120), locationId.toString(),
                        """
                        {"warehouseId":"%s","lines":[{"locationId":"%s","skuId":"%s",
                        "availableQtyAfter":80,"reservedQtyAfter":20}]}"""
                                .formatted(warehouseId, locationId, skuId)));
        kafkaTemplate.send("wms.inventory.released.v1", locationId.toString(),
                KafkaTestSupport.envelope(UUID.randomUUID(), "inventory.released",
                        Instant.now().minusSeconds(60), locationId.toString(),
                        """
                        {"warehouseId":"%s","lines":[{"locationId":"%s","skuId":"%s",
                        "availableQtyAfter":85,"reservedQtyAfter":15}]}"""
                                .formatted(warehouseId, locationId, skuId)));

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(snapshotRepo.findById(snapshotId)).isPresent()
                        .get().satisfies(s -> {
                            assertThat(s.getAvailableQty()).isEqualTo(s1Available);
                            assertThat(s.getReservedQty()).isEqualTo(s1Reserved);
                        }));
    }

    private double errorCount(String topic) {
        var counter = meterRegistry.find("admin.projection.error.count")
                .tag("topic", topic).counter();
        return counter == null ? 0.0d : counter.count();
    }
}
