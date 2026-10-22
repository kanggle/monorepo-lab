package com.wms.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.IntNode;
import com.wms.admin.api.dashboard.InventoryDashboardController;
import com.wms.admin.api.dashboard.dto.InventorySnapshotResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.application.settings.LowStockThresholdReader;
import com.wms.admin.application.settings.SettingsService;
import com.wms.admin.application.settings.UpsertSettingCommand;
import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-BE-644 — against real PostgreSQL, a <b>setting change alone</b> changes
 * the low-stock result for rows that receive no inventory event.
 *
 * <p>This is the failure the ticket names: the old stored flag was written at
 * projection time against a constant, so changing
 * {@code inventory.low_stock.default_threshold_qty} never reached existing rows.
 * The rows below are written once and never touched again; only the setting
 * moves. A per-warehouse id scopes every query so rows left by sibling classes
 * on the shared container cannot satisfy or break an assertion.
 *
 * <p>The setting is first upserted to a known value rather than trusting the
 * seed's 10, because the container is shared and reused.
 */
@Tag("integration")
@SpringBootTest(classes = com.wms.admin.AdminServiceApplication.class,
        properties = {
                // 🔴 Same location list as every other class on this container (see
                // ReadModelPersistenceIntegrationTest) — and the seed is needed here:
                // the setting row must exist for SettingsService.upsert to accept it.
                "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@ActiveProfiles({"test", "standalone"})
@ContextConfiguration(initializers = AdminServiceIntegrationBase.Initializer.class)
@Transactional
@WithMockUser(username = "ops-it", roles = {"WMS_ADMIN", "WMS_VIEWER"})
class InventoryLowStockThresholdIT extends AdminServiceIntegrationBase {

    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

    @Autowired InventorySnapshotRepository snapshotRepo;
    @Autowired SettingsService settingsService;
    @Autowired InventoryDashboardController controller;
    @Autowired DataSource dataSource;

    private void setThreshold(int value) {
        settingsService.upsert(new UpsertSettingCommand(LowStockThresholdReader.KEY, null,
                IntNode.valueOf(value), "ops-it"));
    }

    private PageResponse<InventorySnapshotResponse> lowStockOnly(UUID warehouseId) {
        return controller.list(warehouseId, null, null, null, true, null, 0, 50, "lastEventAt,desc");
    }

    private PageResponse<InventorySnapshotResponse> all(UUID warehouseId) {
        return controller.list(warehouseId, null, null, null, false, null, 0, 50, "lastEventAt,desc");
    }

    private void row(UUID warehouseId, int availableQty) {
        snapshotRepo.saveAndFlush(new InventorySnapshotEntity(UUID.randomUUID(), UUID.randomUUID(),
                null, warehouseId, null, "LOC-" + availableQty, "SKU-" + availableQty, null,
                availableQty, 0, 0, NOW, NOW));
    }

    @Test
    void settingChangeAlone_changesLowStockFilterAndFlag() {
        UUID wh = UUID.randomUUID();
        row(wh, 5);
        row(wh, 10);
        row(wh, 15);
        row(wh, 25);

        setThreshold(10);
        assertThat(lowStockOnly(wh).content()).extracting(InventorySnapshotResponse::availableQty)
                .containsExactlyInAnyOrder(5); // 10 is AT the threshold → not low (strict <)

        // No inventory event between these two reads — only the setting moves.
        setThreshold(25);
        assertThat(lowStockOnly(wh).content()).extracting(InventorySnapshotResponse::availableQty)
                .containsExactlyInAnyOrder(5, 10, 15);
        assertThat(all(wh).content())
                .allSatisfy(r -> assertThat(r.lowStockFlag()).isEqualTo(r.availableQty() < 25));

        // And back down: nothing is "stuck" on the higher threshold.
        setThreshold(0);
        assertThat(lowStockOnly(wh).content()).isEmpty();
        assertThat(all(wh).page().totalElements()).isEqualTo(4);
    }

    @Test
    void v5_droppedTheStoredFlagAndItsIndex() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer column = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 'admin_inventory_snapshot' AND column_name = 'low_stock_flag'",
                Integer.class);
        Integer index = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_admin_inventory_snapshot_low_stock'",
                Integer.class);
        assertThat(column).isZero();
        assertThat(index).isZero();
    }
}
