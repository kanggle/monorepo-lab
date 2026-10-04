package com.example.scmplatform.inventoryvisibility.integration;

import com.example.common.page.PageQuery;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TASK-MONO-760 — with a non-default projection tenant, a wms event lands under that
 * tenant and the read path scoped to that tenant returns it.
 *
 * <p>This is the demo's shape: operators read as {@code demo-corp}. Before the projection
 * tenant existed, the consumers wrote under a hard-coded {@code scm}, so the console's
 * {@code demo-corp} read was empty even when the whole event path worked (19th/20th demo
 * windows). The {@code scm} assertion is the control: the row must not land there.
 */
@Tag("integration")
@TestPropertySource(properties = "inventory-visibility.projection-tenant-id=" + ProjectionTenantIntegrationTest.TENANT)
@DisplayName("IT: projection tenant — wms event lands under the configured tenant and reads back (TASK-MONO-760)")
class ProjectionTenantIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    static final String TENANT = "demo-corp";

    @Autowired
    private InventoryVisibilityApplicationService applicationService;

    @Test
    @DisplayName("received → node + snapshot under demo-corp, readable as demo-corp, nothing under scm")
    void receivedEvent_landsUnderProjectionTenant() {
        String warehouseId = "wh-it-projection-" + UUID.randomUUID();
        String skuId = "sku-it-projection-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        publish(TOPIC_INVENTORY_RECEIVED, eventId.toString(),
                receivedEnvelope(eventId, Instant.now(), warehouseId, skuId, 9));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventJpa.findById(eventId.toString())).isPresent();
            assertThat(nodeJpa.findByTenantIdAndNodeExternalId(TENANT, warehouseId))
                    .as("node auto-registered under the projection tenant").isPresent();
        });

        assertThat(nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouseId))
                .as("control: nothing under the default tenant").isEmpty();

        List<InventorySnapshot> readAsProjectionTenant =
                applicationService.getCrossNodeSnapshot(TENANT, PageQuery.of(0, 100, null, null)).content();
        assertThat(readAsProjectionTenant)
                .as("the read path scoped to the projection tenant sees the row")
                .anyMatch(s -> s.getSku().value().equals(skuId));

        List<InventorySnapshot> readAsScm =
                applicationService.getCrossNodeSnapshot(TENANT_SCM, PageQuery.of(0, 100, null, null)).content();
        assertThat(readAsScm)
                .as("control: the default tenant's read does not see it")
                .noneMatch(s -> s.getSku().value().equals(skuId));
    }
}
