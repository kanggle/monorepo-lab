package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventorySnapshotJpaEntity;
import org.junit.jupiter.api.DisplayName;
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
 * IT-1: {@code wms.inventory.adjusted.v1} consumer apply chain.
 *
 * <p>Publishes the wms-platform global envelope to the topic the service
 * subscribes to (pre-created via AbstractInventoryVisibilityIntegrationTest
 * AdminClient) and asserts the snapshot is upserted.
 *
 * <p>Doubles as the regression guard for TASK-SCM-INT-001b root cause #2 —
 * the auto-created {@code InventoryNode} writes {@code contact_info=null}
 * into a JSONB column.  Removing {@code @JdbcTypeCode(SqlTypes.JSON)} from
 * {@link InventoryNodeJpaEntity#getContactInfo()} causes Hibernate 6 to
 * send {@code bytea} which Postgres rejects (SQLSTATE 42804) — this test
 * fails on the first event.
 */
@DisplayName("IT-1: wms.inventory.adjusted consumer apply")
class WmsInventoryAdjustedConsumerIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Test
    @DisplayName("adjusted (delta=+10) → 신규 snapshot row INSERT + auto-created node + dedupe row")
    void adjusted_createsSnapshot_autoCreatesNode_andDedupeRecord() {
        String warehouseExternalId = "wh-it-adjusted-" + UUID.randomUUID();
        String skuId = "sku-it-adjusted-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        publish(TOPIC_INVENTORY_ADJUSTED, eventId.toString(),
                adjustedEnvelope(eventId, occurredAt, warehouseExternalId, skuId, 10));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            // Node was auto-created with contact_info=null (JSONB null write).
            Optional<InventoryNodeJpaEntity> node =
                    nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseExternalId);
            assertThat(node).as("InventoryNode auto-created").isPresent();
            assertThat(node.get().getContactInfo()).isNull();

            // Snapshot upserted with quantity = +10.
            String nodeId = node.get().getId();
            List<InventorySnapshotJpaEntity> snapshots =
                    snapshotJpa.findAll().stream()
                            .filter(s -> s.getNodeId().equals(nodeId))
                            .filter(s -> s.getSku().equals(skuId))
                            .toList();
            assertThat(snapshots).hasSize(1);
            assertThat(snapshots.get(0).getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(snapshots.get(0).getTenantId()).isEqualTo(TENANT_SCM);

            // Dedupe row written.
            assertThat(dedupeJpa.findById(eventId.toString())).isPresent();
        });
    }

    @Test
    @DisplayName("adjusted (delta=-3) on existing snapshot subtracts quantity")
    void adjusted_negativeDelta_onExistingSnapshot_subtractsQuantity() {
        String warehouseExternalId = "wh-it-neg-" + UUID.randomUUID();
        String skuId = "sku-it-neg-" + UUID.randomUUID();

        // Seed: positive +20
        UUID firstEvent = UUID.randomUUID();
        publish(TOPIC_INVENTORY_ADJUSTED, firstEvent.toString(),
                adjustedEnvelope(firstEvent, Instant.now(), warehouseExternalId, skuId, 20));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(dedupeJpa.findById(firstEvent.toString())).isPresent());

        // Then negative -3
        UUID secondEvent = UUID.randomUUID();
        publish(TOPIC_INVENTORY_ADJUSTED, secondEvent.toString(),
                adjustedEnvelope(secondEvent, Instant.now(), warehouseExternalId, skuId, -3));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(dedupeJpa.findById(secondEvent.toString())).isPresent();
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseExternalId)
                    .orElseThrow().getId();
            BigDecimal qty = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qty).isEqualByComparingTo(new BigDecimal("17"));
        });
    }
}
