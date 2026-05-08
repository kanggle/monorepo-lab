package com.example.scmplatform.inventoryvisibility.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * IT-3: T8 idempotency dedupe.
 *
 * <p>Publishing the same {@code eventId} twice on
 * {@code wms.inventory.adjusted.v1} must result in exactly one snapshot
 * mutation — the second delivery is a silent skip via the
 * {@link com.example.scmplatform.inventoryvisibility.application.port.outbound.EventDedupePort}
 * short-circuit (DB UNIQUE on {@code event_dedupe.event_id} is the safety net).
 *
 * <p>Mirrors TASK-SCM-INT-001b's cross-service dedupe verification but at
 * the service-internal IT layer so a regression catches in 1.5 min, not
 * 5 minutes.
 */
@DisplayName("IT-3: event dedupe (T8)")
class EventDedupeIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Test
    @DisplayName("동일 eventId 2회 publish → 1번만 snapshot 변동, 2번째는 silent skip")
    void duplicateEventId_appliedOnlyOnce() {
        String warehouseId = "wh-it-dedupe-" + UUID.randomUUID();
        String skuId = "sku-it-dedupe-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        String envelope = adjustedEnvelope(eventId, occurredAt, warehouseId, skuId, 5);

        publish(TOPIC_INVENTORY_ADJUSTED, eventId.toString(), envelope);
        publish(TOPIC_INVENTORY_ADJUSTED, eventId.toString(), envelope);

        // First delivery → snapshot quantity = 5, dedupe row written.
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(dedupeJpa.findById(eventId.toString())).isPresent();
            String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                    .orElseThrow().getId();
            BigDecimal qty = snapshotJpa.findAll().stream()
                    .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                    .findFirst().orElseThrow().getQuantity();
            assertThat(qty).isEqualByComparingTo(new BigDecimal("5"));
        });

        // Give the second delivery a fair window to land then re-assert no
        // additional mutation occurred.
        await().pollDelay(3, TimeUnit.SECONDS)
                .atMost(8, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    String nodeId = nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId)
                            .orElseThrow().getId();
                    BigDecimal qty = snapshotJpa.findAll().stream()
                            .filter(s -> s.getNodeId().equals(nodeId) && s.getSku().equals(skuId))
                            .findFirst().orElseThrow().getQuantity();
                    // Quantity unchanged — duplicate did NOT add another +5.
                    assertThat(qty).isEqualByComparingTo(new BigDecimal("5"));
                    // Exactly one dedupe row.
                    long dedupeRows = dedupeJpa.findAll().stream()
                            .filter(d -> d.getEventId().equals(eventId.toString()))
                            .count();
                    assertThat(dedupeRows).isEqualTo(1);
                });
    }
}
