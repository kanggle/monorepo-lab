package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventorySnapshotJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

            assertThat(dedupeJpa.findById(eventId.toString())).isPresent();
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
            assertThat(dedupeJpa.findById(first.toString())).isPresent();
            assertThat(nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getWarehouseCode()).isEqualTo("WH01");
        });

        // 2) A later event WITHOUT the code (wms resolves it best-effort, so this happens).
        //    Set-if-present: the stored code must survive.
        UUID second = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, second.toString(),
                receivedEnvelope(second, Instant.now(), warehouseId, skuId, 4, null));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(dedupeJpa.findById(second.toString())).isPresent();
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
    @DisplayName("두 received 이벤트 누적 시 snapshot quantity가 add 된다")
    void receivedEvent_twiceForSameSku_accumulatesQuantity() {
        String warehouseId = "wh-it-received-acc-" + UUID.randomUUID();
        String skuId = "sku-it-received-acc-" + UUID.randomUUID();

        UUID first = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, first.toString(),
                receivedEnvelope(first, Instant.now(), warehouseId, skuId, 7));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(dedupeJpa.findById(first.toString())).isPresent());

        UUID second = UUID.randomUUID();
        publish(TOPIC_INVENTORY_RECEIVED, second.toString(),
                receivedEnvelope(second, Instant.now(), warehouseId, skuId, 4));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(dedupeJpa.findById(second.toString())).isPresent();
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            BigDecimal qty = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qty).isEqualByComparingTo(new BigDecimal("11"));
        });
    }
}
