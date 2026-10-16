package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.StockAdjustmentRepository;
import com.wms.inventory.application.port.out.StockTransferRepository;
import com.wms.inventory.application.query.AdjustmentListCriteria;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.query.TransferListCriteria;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TASK-BE-637: the four list queries map native-query rows ({@code Object[]}) and read the
 * timestamp columns. On the service's real driver and dialect (PostgreSQL 16 +
 * Hibernate 6) a {@code TIMESTAMPTZ} column comes back as {@link java.time.Instant}, not
 * {@link java.sql.Timestamp}; the repositories used to cast to {@code Timestamp} and threw
 * {@code ClassCastException} (HTTP 500 on every list endpoint).
 *
 * <p>This runs against the Testcontainers Postgres through the repository ports - no mocked
 * {@code Object[]} rows. A mocked row would carry whatever class the test author typed, which
 * is exactly how the defect passed the existing suites.
 */
class NativeListQueryTimestampIntegrationTest extends InventoryServiceIntegrationBase {

    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private StockAdjustmentRepository adjustmentRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private JdbcTemplate jdbc;

    private final UUID warehouseId = UUID.randomUUID();
    private final UUID inventoryId = UUID.randomUUID();
    // Microsecond precision: TIMESTAMPTZ stores micros, so a nanosecond clock would not round-trip.
    private final OffsetDateTime stamp =
            OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("""
                INSERT INTO inventory
                (id, warehouse_id, location_id, sku_id, lot_id,
                 available_qty, reserved_qty, damaged_qty,
                 last_movement_at, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, NULL, 10, 0, 0, ?, 0, ?, 'test', ?, 'test')
                """, inventoryId, warehouseId, UUID.randomUUID(), UUID.randomUUID(),
                stamp, stamp, stamp);
        jdbc.update("""
                INSERT INTO stock_adjustment
                (id, inventory_id, bucket, delta, reason_code, reason_note, actor_id,
                 version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, 'AVAILABLE', -1, 'ADJUSTMENT_LOSS', 'lost in count', 'actor-1',
                        0, ?, 'test', ?, 'test')
                """, UUID.randomUUID(), inventoryId, stamp, stamp);
        jdbc.update("""
                INSERT INTO stock_transfer
                (id, warehouse_id, source_location_id, target_location_id, sku_id, lot_id,
                 quantity, reason_code, reason_note, actor_id,
                 version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, NULL, 3, 'TRANSFER_INTERNAL', 'rebalance', 'actor-1',
                        0, ?, 'test', ?, 'test')
                """, UUID.randomUUID(), warehouseId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), stamp, stamp);
        jdbc.update("""
                INSERT INTO inventory_movement
                (id, inventory_id, movement_type, bucket, delta, qty_before, qty_after,
                 reason_code, actor_id, occurred_at, created_at)
                VALUES (?, ?, 'ADJUSTMENT', 'AVAILABLE', -1, 11, 10,
                        'ADJUSTMENT_LOSS', 'actor-1', ?, ?)
                """, UUID.randomUUID(), inventoryId, stamp, stamp);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stock_adjustment");
        jdbc.update("DELETE FROM stock_transfer");
        // TRUNCATE: inventory_movement has an append-only BEFORE DELETE trigger.
        jdbc.update("TRUNCATE TABLE inventory_movement");
        jdbc.update("DELETE FROM inventory");
    }

    @Test
    @DisplayName("GET /inventory list query returns the row with its timestamps")
    void inventoryList() {
        var page = inventoryRepository.listViews(
                new InventoryListCriteria(warehouseId, null, null, null, null, null, 0, 20, null));

        assertThat(page.content()).hasSize(1);
        var view = page.content().get(0);
        assertThat(view.id()).isEqualTo(inventoryId);
        assertThat(view.lastMovementAt()).isEqualTo(stamp.toInstant());
        assertThat(view.createdAt()).isEqualTo(stamp.toInstant());
        assertThat(view.updatedAt()).isEqualTo(stamp.toInstant());
    }

    @Test
    @DisplayName("transfer list query returns the row with its timestamp")
    void transferList() {
        var page = transferRepository.list(new TransferListCriteria(
                warehouseId, null, null, null, null, null, null, 0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).createdAt()).isEqualTo(stamp.toInstant());
    }

    @Test
    @DisplayName("adjustment list query returns the row with its timestamp")
    void adjustmentList() {
        var page = adjustmentRepository.list(new AdjustmentListCriteria(
                inventoryId, null, null, null, 0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).createdAt()).isEqualTo(stamp.toInstant());
    }

    @Test
    @DisplayName("movement list query returns the row with its timestamp")
    void movementList() {
        var page = movementRepository.list(new MovementListCriteria(
                inventoryId, null, null, null, null, null, null, null, 0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).occurredAt()).isEqualTo(stamp.toInstant());
    }
}
