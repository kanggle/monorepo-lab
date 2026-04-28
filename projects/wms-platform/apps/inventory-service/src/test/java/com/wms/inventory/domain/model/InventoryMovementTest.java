package com.wms.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryMovementTest {

    private static final UUID INVENTORY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");

    @Test
    void factoryEnforcesQtyAfterEqualsBeforePlusDelta() {
        InventoryMovement movement = InventoryMovement.create(
                INVENTORY_ID, MovementType.RECEIVE, Bucket.AVAILABLE,
                10, 0, 10,
                ReasonCode.PUTAWAY, null, null, null, null,
                UUID.randomUUID(), "actor", NOW);
        assertThat(movement.qtyAfter()).isEqualTo(10);
    }

    @Test
    void factoryRejectsInconsistentBeforeAfterDelta() {
        assertThatThrownBy(() -> InventoryMovement.create(
                INVENTORY_ID, MovementType.RECEIVE, Bucket.AVAILABLE,
                10, 0, 99,
                ReasonCode.PUTAWAY, null, null, null, null,
                UUID.randomUUID(), "actor", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("structural invariant");
    }

    @Test
    void factoryRejectsNegativeQtyAfter() {
        assertThatThrownBy(() -> InventoryMovement.create(
                INVENTORY_ID, MovementType.ADJUSTMENT, Bucket.AVAILABLE,
                -5, 3, -2,
                ReasonCode.ADJUSTMENT_LOSS, "lost stock count error fix", null, null, null,
                null, "actor", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restoreRoundtripsAllFields() {
        UUID id = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        InventoryMovement movement = InventoryMovement.restore(
                id, INVENTORY_ID, MovementType.RESERVE, Bucket.AVAILABLE,
                -5, 50, 45,
                ReasonCode.PICKING, null, reservation, null, null,
                null, "actor", NOW);
        assertThat(movement.id()).isEqualTo(id);
        assertThat(movement.reservationId()).isEqualTo(reservation);
        assertThat(movement.bucket()).isEqualTo(Bucket.AVAILABLE);
        assertThat(movement.delta()).isEqualTo(-5);
    }
}
