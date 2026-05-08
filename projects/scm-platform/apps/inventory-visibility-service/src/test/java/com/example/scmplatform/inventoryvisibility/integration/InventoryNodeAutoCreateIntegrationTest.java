package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * IT-4: regression guard for TASK-SCM-INT-001b root cause #2 —
 * Hibernate 6 + Postgres JSONB null write.
 *
 * <p>Drives the auto-register-node path
 * (See {@code InventoryVisibilityApplicationService.resolveOrCreateNode})
 * which inserts an {@code inventory_nodes} row with {@code contact_info=null}
 * into a {@code JSONB} column. Without {@code @JdbcTypeCode(SqlTypes.JSON)}
 * on {@link InventoryNodeJpaEntity#getContactInfo()}, Hibernate 6 sends
 * {@code bytea}, Postgres rejects the cast with SQLSTATE 42804 and the
 * insert fails — and so does this test.
 *
 * <p>How to verify the guard: temporarily comment out
 * {@code @JdbcTypeCode(SqlTypes.JSON)} on {@code contactInfo}, run
 * {@code :inventory-visibility-service:integrationTest}; expect this test
 * to fail with {@code DataIntegrityViolationException} ⊇ {@code 42804}.
 * Then restore the annotation.
 */
@DisplayName("IT-4: InventoryNode auto-create + JSONB null write regression guard")
class InventoryNodeAutoCreateIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Test
    @DisplayName("새 nodeExternalId → inventory_nodes row 자동 생성 + contact_info=null JSONB write 정상")
    void newNodeExternalId_autoCreatesNode_withNullJsonbContactInfo() {
        String warehouseId = "wh-jsonb-guard-" + UUID.randomUUID();
        String skuId = "sku-jsonb-guard-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        publish(TOPIC_INVENTORY_ADJUSTED, eventId.toString(),
                adjustedEnvelope(eventId, Instant.now(), warehouseId, skuId, 1));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<InventoryNodeJpaEntity> node =
                    nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId);
            assertThat(node).as("auto-created InventoryNode").isPresent();
            // The crucial assertion — null JSONB round-trip succeeded.
            assertThat(node.get().getContactInfo()).isNull();
            assertThat(node.get().getNodeType())
                    .isEqualTo(InventoryNodeJpaEntity.NodeTypeJpa.WMS_WAREHOUSE);
            assertThat(node.get().getStatus())
                    .isEqualTo(InventoryNodeJpaEntity.NodeStatusJpa.ACTIVE);
            assertThat(dedupeJpa.findById(eventId.toString())).isPresent();
        });
    }
}
