package com.wms.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inventory.domain.exception.InventoryValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryTest {

    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID SKU = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");

    @Test
    void receivePositiveQtyIncrementsAvailable() {
        Inventory inv = Inventory.createEmpty(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                NOW, "actor");

        InventoryMovement movement = inv.receive(50, ReasonCode.PUTAWAY,
                UUID.randomUUID(), "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(50);
        assertThat(inv.reservedQty()).isZero();
        assertThat(inv.damagedQty()).isZero();
        assertThat(inv.onHandQty()).isEqualTo(50);
        assertThat(inv.lastMovementAt()).isEqualTo(NOW);

        assertThat(movement.movementType()).isEqualTo(MovementType.RECEIVE);
        assertThat(movement.bucket()).isEqualTo(Bucket.AVAILABLE);
        assertThat(movement.delta()).isEqualTo(50);
        assertThat(movement.qtyBefore()).isZero();
        assertThat(movement.qtyAfter()).isEqualTo(50);
        assertThat(movement.reasonCode()).isEqualTo(ReasonCode.PUTAWAY);
    }

    @Test
    void multipleReceivesAccumulate() {
        Inventory inv = Inventory.createEmpty(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                NOW, "actor");
        inv.receive(50, ReasonCode.PUTAWAY, UUID.randomUUID(), "actor", NOW);
        inv.receive(30, ReasonCode.PUTAWAY, UUID.randomUUID(), "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(80);
    }

    @Test
    void receiveZeroQtyIsRejected() {
        Inventory inv = Inventory.createEmpty(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                NOW, "actor");
        assertThatThrownBy(() -> inv.receive(0, ReasonCode.PUTAWAY,
                UUID.randomUUID(), "actor", NOW))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void receiveNegativeQtyIsRejected() {
        Inventory inv = Inventory.createEmpty(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                NOW, "actor");
        assertThatThrownBy(() -> inv.receive(-5, ReasonCode.PUTAWAY,
                UUID.randomUUID(), "actor", NOW))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void onHandQtySumsAllBuckets() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                40, 25, 5, NOW, 3L, NOW, "actor", NOW, "actor");
        assertThat(inv.onHandQty()).isEqualTo(70);
    }

    @Test
    void restoreFailsOnNegativeBucket() {
        assertThatThrownBy(() -> Inventory.restore(
                UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                -1, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- reserve / release / confirm (BE-023) -------------------------------

    @Test
    void reserveMovesQuantityFromAvailableToReserved() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                100, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        UUID reservationId = UUID.randomUUID();

        var movements = inv.reserve(30, reservationId,
                com.wms.inventory.domain.model.ReasonCode.PICKING,
                null, "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(70);
        assertThat(inv.reservedQty()).isEqualTo(30);
        assertThat(movements).hasSize(2);
        assertThat(movements.get(0).bucket()).isEqualTo(Bucket.AVAILABLE);
        assertThat(movements.get(0).delta()).isEqualTo(-30);
        assertThat(movements.get(1).bucket()).isEqualTo(Bucket.RESERVED);
        assertThat(movements.get(1).delta()).isEqualTo(30);
        assertThat(movements.get(0).reservationId()).isEqualTo(reservationId);
        assertThat(movements.get(1).reservationId()).isEqualTo(reservationId);
    }

    @Test
    void reserveInsufficientAvailableThrows() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                10, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        assertThatThrownBy(() -> inv.reserve(50, UUID.randomUUID(),
                com.wms.inventory.domain.model.ReasonCode.PICKING, null, "actor", NOW))
                .isInstanceOf(com.wms.inventory.domain.exception.InsufficientStockException.class);
    }

    @Test
    void confirmDecrementsReservedOnly() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                70, 30, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        UUID reservationId = UUID.randomUUID();

        var movement = inv.confirm(30, reservationId, null, "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(70);  // unchanged (W5)
        assertThat(inv.reservedQty()).isEqualTo(0);
        assertThat(movement.bucket()).isEqualTo(Bucket.RESERVED);
        assertThat(movement.delta()).isEqualTo(-30);
        assertThat(movement.movementType()).isEqualTo(MovementType.CONFIRM);
    }

    @Test
    void releaseMovesReservedBackToAvailable() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                70, 30, 0, NOW, 0L, NOW, "actor", NOW, "actor");

        var movements = inv.release(30, UUID.randomUUID(),
                com.wms.inventory.domain.model.ReasonCode.PICKING_CANCELLED,
                null, "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(100);
        assertThat(inv.reservedQty()).isEqualTo(0);
        assertThat(movements).hasSize(2);
        assertThat(movements.get(0).bucket()).isEqualTo(Bucket.RESERVED);
        assertThat(movements.get(0).delta()).isEqualTo(-30);
        assertThat(movements.get(1).bucket()).isEqualTo(Bucket.AVAILABLE);
        assertThat(movements.get(1).delta()).isEqualTo(30);
    }

    @Test
    void releaseInsufficientReservedThrows() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                100, 5, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        assertThatThrownBy(() -> inv.release(10, UUID.randomUUID(),
                com.wms.inventory.domain.model.ReasonCode.PICKING_CANCELLED,
                null, "actor", NOW))
                .isInstanceOf(com.wms.inventory.domain.exception.InsufficientStockException.class);
    }

    // ---- adjust / transfer / damage methods (BE-024) ------------------------

    @Test
    void adjustPositiveDeltaIncrementsBucket() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                100, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        UUID adjId = UUID.randomUUID();

        InventoryMovement m = inv.adjust(20, Bucket.AVAILABLE, ReasonCode.ADJUSTMENT_FOUND,
                "found in cycle count", adjId, "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(120);
        assertThat(m.delta()).isEqualTo(20);
        assertThat(m.movementType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(m.adjustmentId()).isEqualTo(adjId);
    }

    @Test
    void adjustNegativeDeltaWithSufficientStockDecrementsBucket() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                100, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");

        InventoryMovement m = inv.adjust(-30, Bucket.AVAILABLE, ReasonCode.ADJUSTMENT_LOSS,
                "lost in transit", UUID.randomUUID(), "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(70);
        assertThat(m.delta()).isEqualTo(-30);
    }

    @Test
    void adjustNegativeDeltaThatWouldUnderflowThrows() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                10, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        assertThatThrownBy(() -> inv.adjust(-50, Bucket.AVAILABLE, ReasonCode.ADJUSTMENT_LOSS,
                "lost", UUID.randomUUID(), "actor", NOW))
                .isInstanceOf(com.wms.inventory.domain.exception.InsufficientStockException.class);
    }

    @Test
    void adjustZeroDeltaThrows() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                100, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        assertThatThrownBy(() -> inv.adjust(0, Bucket.AVAILABLE, ReasonCode.ADJUSTMENT_FOUND,
                "no-op", UUID.randomUUID(), "actor", NOW))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void markDamagedMovesQuantityFromAvailableToDamaged() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                100, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");

        var movements = inv.markDamaged(15, "package torn", UUID.randomUUID(), "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(85);
        assertThat(inv.damagedQty()).isEqualTo(15);
        assertThat(movements).hasSize(2);
        assertThat(movements.get(0).bucket()).isEqualTo(Bucket.AVAILABLE);
        assertThat(movements.get(0).delta()).isEqualTo(-15);
        assertThat(movements.get(0).movementType()).isEqualTo(MovementType.DAMAGE_MARK);
        assertThat(movements.get(1).bucket()).isEqualTo(Bucket.DAMAGED);
        assertThat(movements.get(1).delta()).isEqualTo(15);
    }

    @Test
    void markDamagedInsufficientAvailableThrows() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                5, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        assertThatThrownBy(() -> inv.markDamaged(10, "torn", UUID.randomUUID(), "actor", NOW))
                .isInstanceOf(com.wms.inventory.domain.exception.InsufficientStockException.class);
    }

    @Test
    void writeOffDamagedDecrementsDamaged() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                50, 0, 20, NOW, 0L, NOW, "actor", NOW, "actor");

        InventoryMovement m = inv.writeOffDamaged(8, "complete write-off",
                UUID.randomUUID(), "actor", NOW);

        assertThat(inv.damagedQty()).isEqualTo(12);
        assertThat(inv.availableQty()).isEqualTo(50);
        assertThat(m.movementType()).isEqualTo(MovementType.DAMAGE_WRITE_OFF);
        assertThat(m.delta()).isEqualTo(-8);
    }

    @Test
    void writeOffDamagedInsufficientThrows() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                50, 0, 5, NOW, 0L, NOW, "actor", NOW, "actor");
        assertThatThrownBy(() -> inv.writeOffDamaged(10, "too much",
                UUID.randomUUID(), "actor", NOW))
                .isInstanceOf(com.wms.inventory.domain.exception.InsufficientStockException.class);
    }

    @Test
    void transferOutDecrementsAvailable() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                100, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        UUID transferId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        InventoryMovement m = inv.transferOut(40, transferId, targetId, "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(60);
        assertThat(m.delta()).isEqualTo(-40);
        assertThat(m.movementType()).isEqualTo(MovementType.TRANSFER_OUT);
        assertThat(m.transferId()).isEqualTo(transferId);
    }

    @Test
    void transferOutInsufficientThrows() {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                10, 0, 0, NOW, 0L, NOW, "actor", NOW, "actor");
        assertThatThrownBy(() -> inv.transferOut(50, UUID.randomUUID(), UUID.randomUUID(),
                "actor", NOW))
                .isInstanceOf(com.wms.inventory.domain.exception.InsufficientStockException.class);
    }

    @Test
    void transferInIncrementsAvailable() {
        Inventory inv = Inventory.createEmpty(UUID.randomUUID(), WAREHOUSE, LOCATION, SKU, null,
                NOW, "actor");

        InventoryMovement m = inv.transferIn(40, UUID.randomUUID(), UUID.randomUUID(),
                "actor", NOW);

        assertThat(inv.availableQty()).isEqualTo(40);
        assertThat(m.delta()).isEqualTo(40);
        assertThat(m.movementType()).isEqualTo(MovementType.TRANSFER_IN);
    }
}
