package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-5: cross-tenant fail-closed read.
 *
 * <p>Setup: directly persist an {@code inventory_nodes} + {@code inventory_snapshots}
 * pair under {@code tenant=scm}.  Then call the application service's
 * tenant-scoped read methods using {@code tenant=tenant-other} — the
 * repository layer must filter by tenant_id and surface zero rows.
 *
 * <p>This guards Edge Case #5 from the architecture spec — a foreign tenant
 * never sees another tenant's snapshot, regardless of how the request enters
 * (HTTP / consumer rebroadcast).
 */
@DisplayName("IT-5: multi-tenant isolation (fail-closed)")
class CrossTenantIsolationIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Autowired
    private InventoryVisibilityApplicationService applicationService;

    @Test
    @DisplayName("tenant=scm 데이터를 tenant=other로 조회하면 빈 결과")
    void crossTenantReadReturnsEmpty() {
        // Seed under TENANT_SCM
        var node = persistNode(TENANT_SCM, "wh-cross-tenant-" + java.util.UUID.randomUUID());
        // Drive the apply path so a snapshot is created in TENANT_SCM
        applicationService.applyInventoryAdjusted(
                node.getNodeExternalId(), "sku-cross-tenant", 5L,
                java.util.UUID.randomUUID(), java.time.Instant.now(),
                TENANT_SCM, "wms.inventory.adjusted.v1");

        // tenant=scm — sees the snapshot
        long scmCount = applicationService.countCrossNodeSnapshot(TENANT_SCM);
        assertThat(scmCount).isGreaterThan(0);

        // tenant=other — does NOT see scm data
        long otherCount = applicationService.countCrossNodeSnapshot(TENANT_OTHER);
        assertThat(otherCount).isZero();

        var otherSnapshots =
                applicationService.getCrossNodeSnapshot(TENANT_OTHER, 0, 50);
        assertThat(otherSnapshots).isEmpty();

        // SKU-scoped read also fail-closed
        List<?> bySku = applicationService.getSnapshotBySku("sku-cross-tenant", TENANT_OTHER);
        assertThat(bySku).isEmpty();
    }
}
