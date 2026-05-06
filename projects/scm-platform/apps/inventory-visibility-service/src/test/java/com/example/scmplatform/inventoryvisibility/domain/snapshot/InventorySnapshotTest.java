package com.example.scmplatform.inventoryvisibility.domain.snapshot;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventorySnapshotTest {

    private final NodeId nodeId = NodeId.of(UUID.randomUUID());
    private final Sku sku = Sku.of("SKU-001");
    private final UUID eventId1 = UUID.randomUUID();
    private final UUID eventId2 = UUID.randomUUID();
    private final Instant t1 = Instant.parse("2026-05-01T10:00:00Z");
    private final Instant t2 = Instant.parse("2026-05-01T10:05:00Z");

    @Test
    void create_setsInitialQuantityAndVersion() {
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, sku, "scm", Quantity.of(50), eventId1, t1);

        assertThat(snapshot.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(snapshot.getVersion()).isEqualTo(0);
        assertThat(snapshot.getLastEventId()).isEqualTo(eventId1);
        assertThat(snapshot.getSku()).isEqualTo(sku);
    }

    @Test
    void applyDelta_addition_increasesQuantity_andIncrementsVersion() {
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, sku, "scm", Quantity.of(50), eventId1, t1);

        snapshot.applyDelta(Quantity.of(20), true, eventId2, t2);

        assertThat(snapshot.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(70));
        assertThat(snapshot.getVersion()).isEqualTo(1);
        assertThat(snapshot.getLastEventId()).isEqualTo(eventId2);
    }

    @Test
    void applyDelta_subtraction_decreasesQuantity_andIncrementsVersion() {
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, sku, "scm", Quantity.of(50), eventId1, t1);

        snapshot.applyDelta(Quantity.of(10), false, eventId2, t2);

        assertThat(snapshot.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(snapshot.getVersion()).isEqualTo(1);
    }

    @Test
    void applyDelta_subtraction_cannotGoBelowZero() {
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, sku, "scm", Quantity.of(5), eventId1, t1);

        // Subtract more than available — floor at zero
        snapshot.applyDelta(Quantity.of(100), false, eventId2, t2);

        assertThat(snapshot.getQuantity().value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyQuantity_replacesAbsoluteValue_andIncrementsVersion() {
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, sku, "scm", Quantity.of(50), eventId1, t1);

        snapshot.applyQuantity(Quantity.of(30), eventId2, t2);

        assertThat(snapshot.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(30));
        assertThat(snapshot.getVersion()).isEqualTo(1);
    }

    @Test
    void multipleUpserts_versionIncrementsCorrectly() {
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, sku, "scm", Quantity.of(10), eventId1, t1);

        for (int i = 0; i < 5; i++) {
            snapshot.applyDelta(Quantity.of(1), true, UUID.randomUUID(), t2);
        }

        assertThat(snapshot.getVersion()).isEqualTo(5);
        assertThat(snapshot.getQuantity().value()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }

    @Test
    void quantity_negativeShouldThrow() {
        assertThatThrownBy(() -> Quantity.of(BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}
