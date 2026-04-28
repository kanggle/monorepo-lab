package com.wms.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inventory.domain.exception.InventoryValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockAdjustmentTest {

    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");

    @Test
    void createPopulatesAuditFields() {
        UUID inventoryId = UUID.randomUUID();
        StockAdjustment a = StockAdjustment.create(
                inventoryId, Bucket.AVAILABLE, -5,
                ReasonCode.ADJUSTMENT_LOSS, "lost in transit",
                "user-1", "idem-1", NOW);

        assertThat(a.id()).isNotNull();
        assertThat(a.inventoryId()).isEqualTo(inventoryId);
        assertThat(a.bucket()).isEqualTo(Bucket.AVAILABLE);
        assertThat(a.delta()).isEqualTo(-5);
        assertThat(a.reasonCode()).isEqualTo(ReasonCode.ADJUSTMENT_LOSS);
        assertThat(a.reasonNote()).isEqualTo("lost in transit");
        assertThat(a.actorId()).isEqualTo("user-1");
        assertThat(a.idempotencyKey()).isEqualTo("idem-1");
        assertThat(a.version()).isZero();
        assertThat(a.createdAt()).isEqualTo(NOW);
        assertThat(a.createdBy()).isEqualTo("user-1");
    }

    @Test
    void zeroDeltaRejected() {
        assertThatThrownBy(() -> StockAdjustment.create(
                UUID.randomUUID(), Bucket.AVAILABLE, 0,
                ReasonCode.ADJUSTMENT_FOUND, "n/a", "actor", null, NOW))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void shortReasonNoteRejected() {
        assertThatThrownBy(() -> StockAdjustment.create(
                UUID.randomUUID(), Bucket.AVAILABLE, 1,
                ReasonCode.ADJUSTMENT_FOUND, "  ", "actor", null, NOW))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void disallowedReasonCodeRejected() {
        assertThatThrownBy(() -> StockAdjustment.create(
                UUID.randomUUID(), Bucket.AVAILABLE, 1,
                ReasonCode.PUTAWAY, "wrong code for adjustment", "actor", null, NOW))
                .isInstanceOf(InventoryValidationException.class);
    }
}
