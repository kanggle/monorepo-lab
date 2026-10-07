package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventorySnapshotJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * IT-2: {@code wms.inventory.received.v1} consumer apply chain.
 *
 * <p>Replaces the placeholder skeleton from TASK-SCM-BE-003.
 * Mirrors IT-1's structure but exercises the multi-line received payload
 * shape produced by wms-platform's
 * {@code InventoryEventEnvelopeSerializer.receivedPayload(...)}.
 */
@Tag("integration")
@DisplayName("IT-2: wms.inventory.received consumer apply")
class WmsInventoryReceivedConsumerIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Test
    @DisplayName("received (qty=15) → snapshot upsert + auto-created node + dedupe row")
    void receivedEvent_createsSnapshot_andDedupeRecord() {
        String warehouseId = "wh-it-received-" + UUID.randomUUID();
        String skuId = "sku-it-received-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        publish(TOPIC_INVENTORY_RECEIVED, eventId.toString(),
                receivedEnvelope(eventId, Instant.now(), warehouseId, skuId, 15));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<InventoryNodeJpaEntity> node =
                    nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId);
            assertThat(node).as("auto-created node").isPresent();
            assertThat(node.get().getContactInfo()).isNull();

            String nodeId = node.get().getId();
            List<InventorySnapshotJpaEntity> snapshots = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                    .toList();
            assertThat(snapshots).hasSize(1);
            assertThat(snapshots.get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("15"));

            assertThat(processedEventJpa.findById(eventId.toString())).isPresent();
        });
    }

    @Test
    @DisplayName("received with warehouseCode → node persists warehouse_code (ADR-MONO-050 D9)")
    void receivedEvent_persistsWarehouseCodeOnNode() {
        String warehouseId = "wh-it-received-code-" + UUID.randomUUID();
        String skuId = "sku-it-received-code-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        publish(TOPIC_INVENTORY_RECEIVED, eventId.toString(),
                receivedEnvelope(eventId, Instant.now(), warehouseId, skuId, 15, "WH01"));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<InventoryNodeJpaEntity> node =
                    nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId);
            assertThat(node).as("auto-created node").isPresent();
            assertThat(node.get().getWarehouseCode()).isEqualTo("WH01");
        });
    }

    @Test
    @DisplayName("later null-code event must NOT wipe a stored warehouse_code (ADR-MONO-050 D9)")
    void receivedEvent_withoutWarehouseCode_doesNotWipeStoredCode() {
        String warehouseId = "wh-it-received-nowipe-" + UUID.randomUUID();
        String skuId = "sku-it-received-nowipe-" + UUID.randomUUID();

        // 1) An event carrying the code — the node learns it.
        UUID first = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, first.toString(),
                receivedEnvelope(first, Instant.now(), warehouseId, skuId, 7, "WH01"));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(first.toString())).isPresent();
            assertThat(nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getWarehouseCode()).isEqualTo("WH01");
        });

        // 2) A later event WITHOUT the code (wms resolves it best-effort, so this happens).
        //    Set-if-present: the stored code must survive.
        UUID second = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, second.toString(),
                receivedEnvelope(second, Instant.now(), warehouseId, skuId, 4, null));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(second.toString())).isPresent();
            InventoryNodeJpaEntity node =
                    nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId).orElseThrow();
            assertThat(node.getWarehouseCode())
                    .as("null incoming code must not overwrite a stored code")
                    .isEqualTo("WH01");
            // The projection itself is unaffected by the missing code.
            BigDecimal qty = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(node.getId()) && s.getSku().equals(skuId))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qty).isEqualByComparingTo(new BigDecimal("11"));
        });
    }

    @Test
    @DisplayName("TASK-SCM-BE-061 AC-0/AC-1: a 3-line received event applies ALL lines "
            + "(pre-fix: only line 1 — lines 2+ read as a duplicate of the shared eventId)")
    void receivedEvent_threeLines_appliesAllThree() {
        String warehouseId = "wh-it-received-multi-" + UUID.randomUUID();
        String skuA = "sku-it-received-multi-a-" + UUID.randomUUID();
        String skuB = "sku-it-received-multi-b-" + UUID.randomUUID();
        String skuC = "sku-it-received-multi-c-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        publish(TOPIC_INVENTORY_RECEIVED, eventId.toString(),
                receivedEnvelope(eventId, Instant.now(), warehouseId, List.of(
                        Map.entry(skuA, 10L), Map.entry(skuB, 20L), Map.entry(skuC, 30L))));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(eventId.toString())).isPresent();
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            List<InventorySnapshotJpaEntity> snapshots = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId))
                    .toList();
            assertThat(snapshots).as("all 3 lines applied, not just line 1").hasSize(3);
            assertThat(snapshots.stream()
                    .filter(s -> s.getSku().equals(skuA)).findFirst().orElseThrow().getQuantity())
                    .isEqualByComparingTo(new BigDecimal("10"));
            assertThat(snapshots.stream()
                    .filter(s -> s.getSku().equals(skuB)).findFirst().orElseThrow().getQuantity())
                    .isEqualByComparingTo(new BigDecimal("20"));
            assertThat(snapshots.stream()
                    .filter(s -> s.getSku().equals(skuC)).findFirst().orElseThrow().getQuantity())
                    .isEqualByComparingTo(new BigDecimal("30"));
        });
    }

    @Test
    @DisplayName("AC-1: re-delivery of the same multi-line eventId is skipped — no double-apply")
    void receivedEvent_multiLine_duplicateEventId_skipped() {
        String warehouseId = "wh-it-received-multi-dup-" + UUID.randomUUID();
        String skuA = "sku-it-received-multi-dup-a-" + UUID.randomUUID();
        String skuB = "sku-it-received-multi-dup-b-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String envelope = receivedEnvelope(eventId, Instant.now(), warehouseId,
                List.of(Map.entry(skuA, 10L), Map.entry(skuB, 20L)));

        publish(TOPIC_INVENTORY_RECEIVED, eventId.toString(), envelope);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventJpa.findById(eventId.toString())).isPresent());

        // Re-deliver the SAME eventId — must be skipped (T8), not a second apply.
        publish(TOPIC_INVENTORY_RECEIVED, eventId.toString() + "-redelivery", envelope);

        // The post-condition is already true before the re-delivery is even consumed, so
        // without pollDelay this would pass without ever exercising the dedupe path.
        await().pollDelay(5, TimeUnit.SECONDS).atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            List<InventorySnapshotJpaEntity> snapshots = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId))
                    .toList();
            assertThat(snapshots).as("still 2 rows — the re-delivery must not apply a second time")
                    .hasSize(2);
            assertThat(snapshots.stream()
                    .filter(s -> s.getSku().equals(skuA)).findFirst().orElseThrow().getQuantity())
                    .isEqualByComparingTo(new BigDecimal("10"));
            assertThat(snapshots.stream()
                    .filter(s -> s.getSku().equals(skuB)).findFirst().orElseThrow().getQuantity())
                    .isEqualByComparingTo(new BigDecimal("20"));
        });
    }

    @Test
    @DisplayName("Edge Case: a line with a missing skuId fails the WHOLE event — no partial "
            + "apply of the lines before it (transaction/parse boundary covers all lines)")
    void receivedEvent_lineWithMissingSkuId_appliesNoLine() {
        String warehouseId = "wh-it-received-badline-" + UUID.randomUUID();
        String skuA = "sku-it-received-badline-a-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Line 1 is well-formed; line 2 is missing "skuId" entirely.
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", eventId.toString());
        env.put("eventType", "inventory.received");
        env.put("eventVersion", 1);
        env.put("occurredAt", Instant.now().toString());
        env.put("producer", "inventory-service");
        env.put("aggregateType", "inventory");
        env.put("aggregateId", warehouseId);
        env.put("traceId", null);
        env.put("actorId", "test-actor");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("warehouseId", warehouseId);
        payload.put("lines", List.of(
                Map.of("skuId", skuA, "qtyReceived", 10),
                Map.of("qtyReceived", 20))); // no skuId
        env.put("payload", payload);

        publish(TOPIC_INVENTORY_RECEIVED, eventId.toString(), toJson(env));

        // The retry+DLT path takes a few seconds; pollDelay forces waiting it out before
        // asserting, so the assertion is not trivially true at t=0.
        await().pollDelay(10, TimeUnit.SECONDS).atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(eventId.toString())).isEmpty();
            Optional<InventoryNodeJpaEntity> node =
                    nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId);
            assertThat(node).as("no node created — the malformed line fails before any apply")
                    .isEmpty();
            List<InventorySnapshotJpaEntity> snapshots = snapshotJpa.findAll().stream()
                    .filter(s -> s.getSku().equals(skuA))
                    .toList();
            assertThat(snapshots)
                    .as("line 1 (well-formed) must NOT have been applied either — no partial apply")
                    .isEmpty();
        });
    }

    @Test
    @DisplayName("두 received 이벤트 누적 시 snapshot quantity가 add 된다")
    void receivedEvent_twiceForSameSku_accumulatesQuantity() {
        String warehouseId = "wh-it-received-acc-" + UUID.randomUUID();
        String skuId = "sku-it-received-acc-" + UUID.randomUUID();

        UUID first = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, first.toString(),
                receivedEnvelope(first, Instant.now(), warehouseId, skuId, 7));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventJpa.findById(first.toString())).isPresent());

        UUID second = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, second.toString(),
                receivedEnvelope(second, Instant.now(), warehouseId, skuId, 4));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(second.toString())).isPresent();
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            BigDecimal qty = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qty).isEqualByComparingTo(new BigDecimal("11"));
        });
    }
}
