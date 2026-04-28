package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that all V1–V5 migrations apply cleanly against PostgreSQL via
 * Testcontainers, and that the W2 trigger on {@code inventory_movement}
 * rejects {@code UPDATE} and {@code DELETE} from the application's runtime
 * connection.
 */
class FlywayMigrationIntegrationTest extends InventoryServiceIntegrationBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("V1 inventory + movement + outbox + dedupe tables exist")
    void v1_tables_present() {
        assertThat(tableExists("inventory")).isTrue();
        assertThat(tableExists("inventory_movement")).isTrue();
        assertThat(tableExists("inventory_outbox")).isTrue();
        assertThat(tableExists("inventory_event_dedupe")).isTrue();
    }

    @Test
    @DisplayName("V2 reservation + reservation_line tables exist")
    void v2_tables_present() {
        assertThat(tableExists("reservation")).isTrue();
        assertThat(tableExists("reservation_line")).isTrue();
    }

    @Test
    @DisplayName("V3 stock_adjustment + stock_transfer tables exist")
    void v3_tables_present() {
        assertThat(tableExists("stock_adjustment")).isTrue();
        assertThat(tableExists("stock_transfer")).isTrue();
    }

    @Test
    @DisplayName("V4 master read-model snapshot tables exist")
    void v4_tables_present() {
        assertThat(tableExists("location_snapshot")).isTrue();
        assertThat(tableExists("sku_snapshot")).isTrue();
        assertThat(tableExists("lot_snapshot")).isTrue();
    }

    @Test
    @DisplayName("V5 W2 trigger rejects UPDATE on inventory_movement")
    @Transactional
    void w2_trigger_blocks_update() {
        UUID inventoryId = seedInventoryRow();
        UUID movementId = seedMovementRow(inventoryId, 0, 100);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE inventory_movement SET qty_after = qty_after + 1 WHERE id = ?",
                movementId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("V5 W2 trigger rejects DELETE on inventory_movement")
    @Transactional
    void w2_trigger_blocks_delete() {
        UUID inventoryId = seedInventoryRow();
        UUID movementId = seedMovementRow(inventoryId, 0, 100);

        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM inventory_movement WHERE id = ?",
                movementId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("inventory bucket non-negative CHECK constraint enforced")
    @Transactional
    void inventory_buckets_nonneg_constraint() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inventory
                (id, warehouse_id, location_id, sku_id, lot_id,
                 available_qty, reserved_qty, damaged_qty,
                 last_movement_at, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, NULL, ?, 0, 0, ?, 0, ?, 'test', ?, 'test')
                """, id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                -1, now, now, now))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("movement structural CHECK qty_after = qty_before + delta enforced")
    @Transactional
    void movement_structural_check() {
        UUID inventoryId = seedInventoryRow();
        UUID movementId = UUID.randomUUID();
        Instant now = Instant.now();
        // Try to insert a movement where qty_after != qty_before + delta
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inventory_movement
                (id, inventory_id, movement_type, bucket, delta, qty_before, qty_after,
                 reason_code, actor_id, occurred_at, created_at)
                VALUES (?, ?, 'RECEIVE', 'AVAILABLE', 10, 0, 99,
                        'PUTAWAY', 'test', ?, ?)
                """, movementId, inventoryId, now, now))
                .isInstanceOf(DataAccessException.class);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private UUID seedInventoryRow() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> params = Map.of();
        jdbc.update("""
                INSERT INTO inventory
                (id, warehouse_id, location_id, sku_id, lot_id,
                 available_qty, reserved_qty, damaged_qty,
                 last_movement_at, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, NULL, 100, 0, 0, ?, 0, ?, 'test', ?, 'test')
                """, id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now, now, now);
        return id;
    }

    private UUID seedMovementRow(UUID inventoryId, int before, int after) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO inventory_movement
                (id, inventory_id, movement_type, bucket, delta, qty_before, qty_after,
                 reason_code, actor_id, occurred_at, created_at)
                VALUES (?, ?, 'RECEIVE', 'AVAILABLE', ?, ?, ?, 'PUTAWAY', 'test', ?, ?)
                """, id, inventoryId, after - before, before, after, now, now);
        return id;
    }
}
