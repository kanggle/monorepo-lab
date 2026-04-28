package com.wms.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inventory.domain.exception.InventoryValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockTransferTest {

    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");

    @Test
    void createPopulatesAllFields() {
        UUID warehouse = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID sku = UUID.randomUUID();

        StockTransfer t = StockTransfer.create(
                warehouse, source, target, sku, null, 10,
                TransferReasonCode.TRANSFER_INTERNAL, "rebalance",
                "user-1", "idem-1", NOW);

        assertThat(t.id()).isNotNull();
        assertThat(t.warehouseId()).isEqualTo(warehouse);
        assertThat(t.sourceLocationId()).isEqualTo(source);
        assertThat(t.targetLocationId()).isEqualTo(target);
        assertThat(t.skuId()).isEqualTo(sku);
        assertThat(t.lotId()).isNull();
        assertThat(t.quantity()).isEqualTo(10);
        assertThat(t.reasonCode()).isEqualTo(TransferReasonCode.TRANSFER_INTERNAL);
        assertThat(t.reasonNote()).isEqualTo("rebalance");
        assertThat(t.actorId()).isEqualTo("user-1");
    }

    @Test
    void sameLocationRejected() {
        UUID location = UUID.randomUUID();
        assertThatThrownBy(() -> StockTransfer.create(
                UUID.randomUUID(), location, location, UUID.randomUUID(), null, 5,
                TransferReasonCode.TRANSFER_INTERNAL, null, "actor", null, NOW))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void zeroQuantityRejected() {
        assertThatThrownBy(() -> StockTransfer.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, 0,
                TransferReasonCode.TRANSFER_INTERNAL, null, "actor", null, NOW))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void negativeQuantityRejected() {
        assertThatThrownBy(() -> StockTransfer.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, -3,
                TransferReasonCode.TRANSFER_INTERNAL, null, "actor", null, NOW))
                .isInstanceOf(InventoryValidationException.class);
    }
}
