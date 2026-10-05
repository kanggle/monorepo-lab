package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventorySnapshotJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * IT: {@code wms.inventory.confirmed.v1} consumer apply chain (TASK-MONO-762 AC-1/AC-3).
 *
 * <p>AC-0 ⓐ: snapshot quantity means on-hand (available + reserved); only {@code confirmed}
 * decrements it. Scenario mirrors the 21차 데모 window measurement: received 95, confirmed
 * 10 → snapshot settles at 85 (not the pre-fix 95).
 */
@Tag("integration")
@DisplayName("IT: wms.inventory.confirmed consumer apply (TASK-MONO-762)")
class WmsInventoryConfirmedConsumerIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Test
    @DisplayName("AC-1: received 95 then confirmed 10 → snapshot settles at 85")
    void confirmedEvent_decrementsOnHand_afterReceived() {
        String warehouseId = "wh-it-confirmed-" + UUID.randomUUID();
        String skuId = "sku-it-confirmed-" + UUID.randomUUID();

        UUID receivedEventId = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, receivedEventId.toString(),
                receivedEnvelope(receivedEventId, Instant.now(), warehouseId, skuId, 95));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventJpa.findById(receivedEventId.toString())).isPresent());

        UUID confirmedEventId = UUID.randomUUID();
        publish(TOPIC_INVENTORY_CONFIRMED, confirmedEventId.toString(),
                confirmedEnvelope(confirmedEventId, Instant.now(), warehouseId, skuId, 10));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(confirmedEventId.toString())).isPresent();
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            BigDecimal qty = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qty).as("95 received - 10 confirmed = 85")
                    .isEqualByComparingTo(new BigDecimal("85"));
        });
    }

    @Test
    @DisplayName("Control: received-only (no confirmed) stays at 95")
    void receivedOnly_withoutConfirmed_staysAt95() {
        String warehouseId = "wh-it-confirmed-control-" + UUID.randomUUID();
        String skuId = "sku-it-confirmed-control-" + UUID.randomUUID();

        UUID receivedEventId = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, receivedEventId.toString(),
                receivedEnvelope(receivedEventId, Instant.now(), warehouseId, skuId, 95));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(receivedEventId.toString())).isPresent();
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            BigDecimal qty = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qty).isEqualByComparingTo(new BigDecimal("95"));
        });
    }

    @Test
    @DisplayName("Duplicate confirmed eventId is skipped — no double decrement")
    void confirmedEvent_duplicateEventId_skipped() {
        String warehouseId = "wh-it-confirmed-dup-" + UUID.randomUUID();
        String skuId = "sku-it-confirmed-dup-" + UUID.randomUUID();

        UUID receivedEventId = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, receivedEventId.toString(),
                receivedEnvelope(receivedEventId, Instant.now(), warehouseId, skuId, 95));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventJpa.findById(receivedEventId.toString())).isPresent());

        UUID confirmedEventId = UUID.randomUUID();
        String envelope = confirmedEnvelope(confirmedEventId, Instant.now(), warehouseId, skuId, 10);

        publish(TOPIC_INVENTORY_CONFIRMED, confirmedEventId.toString(), envelope);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventJpa.findById(confirmedEventId.toString())).isPresent());

        // Re-deliver the SAME eventId — must be skipped (T8), not a second decrement.
        publish(TOPIC_INVENTORY_CONFIRMED, confirmedEventId.toString() + "-redelivery", envelope);

        // 85 is already true before the re-delivery is consumed, so without pollDelay this
        // would pass without ever exercising the dedupe path.
        await().pollDelay(5, TimeUnit.SECONDS).atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            BigDecimal qty = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qty).as("still 85 — the re-delivery must not decrement a second time")
                    .isEqualByComparingTo(new BigDecimal("85"));
        });
    }

    @Test
    @DisplayName("Multi-line confirmed event decrements each SKU independently")
    void confirmedEvent_multiLine_decrementsEachSkuIndependently() {
        String warehouseId = "wh-it-confirmed-multi-" + UUID.randomUUID();
        String skuA = "sku-it-confirmed-multi-a-" + UUID.randomUUID();
        String skuB = "sku-it-confirmed-multi-b-" + UUID.randomUUID();

        UUID receivedA = UUID.randomUUID();
        UUID receivedB = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, receivedA.toString(),
                receivedEnvelope(receivedA, Instant.now(), warehouseId, skuA, 50));
        publish(TOPIC_INVENTORY_RECEIVED, receivedB.toString(),
                receivedEnvelope(receivedB, Instant.now(), warehouseId, skuB, 30));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(receivedA.toString())).isPresent();
            assertThat(processedEventJpa.findById(receivedB.toString())).isPresent();
        });

        UUID confirmedEventId = UUID.randomUUID();
        publish(TOPIC_INVENTORY_CONFIRMED, confirmedEventId.toString(),
                confirmedEnvelope(confirmedEventId, Instant.now(), warehouseId,
                        List.of(Map.entry(skuA, 5L), Map.entry(skuB, 3L))));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(confirmedEventId.toString())).isPresent();
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            BigDecimal qtyA = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuA))
                    .findFirst().orElseThrow().getQuantity();
            BigDecimal qtyB = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuB))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qtyA).isEqualByComparingTo(new BigDecimal("45"));
            assertThat(qtyB).isEqualByComparingTo(new BigDecimal("27"));
        });
    }

    @Test
    @DisplayName("Edge Case: confirmed arrives before received (no row) → DLT, no row created")
    void confirmedEvent_noExistingSnapshot_routesToDlt_noRowCreated() {
        String warehouseId = "wh-it-confirmed-outoforder-" + UUID.randomUUID();
        String skuId = "sku-it-confirmed-outoforder-" + UUID.randomUUID();
        UUID confirmedEventId = UUID.randomUUID();

        publish(TOPIC_INVENTORY_CONFIRMED, confirmedEventId.toString(),
                confirmedEnvelope(confirmedEventId, Instant.now(), warehouseId, skuId, 10));

        // Retry (3x, 1s/2s backoff) then DLT. The assertion below is already true at t=0
        // (nothing has been consumed yet), so a plain await().untilAsserted would pass
        // instantly without ever exercising the retry→DLT path — pollDelay forces it to
        // wait out the backoff window first, then assert the row was never created.
        await().pollDelay(10, TimeUnit.SECONDS).atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<InventoryNodeJpaEntity> node =
                    nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId);
            assertThat(node).as("no node auto-registered by confirmed").isEmpty();
            assertThat(processedEventJpa.findById(confirmedEventId.toString())).isEmpty();
            List<InventorySnapshotJpaEntity> snapshots = snapshotJpa.findAll().stream()
                    .filter(s -> s.getSku().equals(skuId))
                    .toList();
            assertThat(snapshots).as("no negative/zero row ever created").isEmpty();
        });
    }
}
