package com.example.scmplatform.inventoryvisibility.domain.node;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the node's warehouse-code semantics (ADR-MONO-050 D9 / TASK-SCM-BE-037).
 *
 * <p>wms resolves the code best-effort from its warehouse master read-model and emits
 * {@code null} while that snapshot is unpopulated (startup race). The node therefore
 * applies the code <b>set-if-present</b>: a null incoming value must never erase a code
 * the node already learned, or a single unlucky event would silently break the batch
 * replenishment addressing for that warehouse until the next non-null event.
 */
class InventoryNodeWarehouseCodeTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(600);

    private InventoryNode node(String warehouseCode) {
        return InventoryNode.autoRegisterWmsWarehouse(
                NodeId.of(UUID.randomUUID()), "scm", "WH-EXT-1", warehouseCode, NOW);
    }

    @Test
    void autoRegisterCarriesTheWarehouseCode() {
        assertThat(node("WH01").getWarehouseCode()).isEqualTo("WH01");
    }

    @Test
    void autoRegisterToleratesNullWarehouseCode() {
        InventoryNode n = node(null);
        assertThat(n.getWarehouseCode()).isNull();
        // The node is still registered and usable — a null code never blocks projection.
        assertThat(n.isActive()).isTrue();
        assertThat(n.getNodeExternalId()).isEqualTo("WH-EXT-1");
    }

    @Test
    void firstNonNullCodeIsLearned() {
        InventoryNode n = node(null);

        assertThat(n.applyWarehouseCodeIfPresent("WH01", LATER)).isTrue();
        assertThat(n.getWarehouseCode()).isEqualTo("WH01");
        assertThat(n.getUpdatedAt()).isEqualTo(LATER);
    }

    /** The load-bearing rule: a null incoming code must NOT wipe a stored code. */
    @Test
    void nullIncomingCodeNeverOverwritesStoredCode() {
        InventoryNode n = node("WH01");

        assertThat(n.applyWarehouseCodeIfPresent(null, LATER)).isFalse();
        assertThat(n.getWarehouseCode()).isEqualTo("WH01");
        assertThat(n.getUpdatedAt()).isEqualTo(NOW); // untouched — no redundant write
    }

    @Test
    void unchangedCodeReportsNoChange_soCallerSkipsTheWrite() {
        InventoryNode n = node("WH01");

        assertThat(n.applyWarehouseCodeIfPresent("WH01", LATER)).isFalse();
        assertThat(n.getWarehouseCode()).isEqualTo("WH01");
        assertThat(n.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void differentNonNullCodeReplacesTheStoredOne() {
        InventoryNode n = node("WH01");

        assertThat(n.applyWarehouseCodeIfPresent("WH02", LATER)).isTrue();
        assertThat(n.getWarehouseCode()).isEqualTo("WH02");
        assertThat(n.getUpdatedAt()).isEqualTo(LATER);
    }
}
