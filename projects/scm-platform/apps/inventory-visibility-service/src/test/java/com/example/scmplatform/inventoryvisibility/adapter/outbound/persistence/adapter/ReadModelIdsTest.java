package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.adapter;

import com.example.scmplatform.inventoryvisibility.domain.error.ReadModelCorruptException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ReadModelIds} — the persistence-layer UUID parser that
 * reclassifies a corrupt persisted id as a server data-integrity fault
 * ({@link ReadModelCorruptException}) rather than a bare {@link
 * IllegalArgumentException}. Guards the TASK-MONO-171 root cause.
 */
class ReadModelIdsTest {

    @Test
    @DisplayName("requireUuid parses a well-formed UUID string")
    void parsesValidUuid() {
        UUID expected = UUID.randomUUID();
        assertThat(ReadModelIds.requireUuid(expected.toString(), "inventory_nodes.id"))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("requireUuid wraps a non-UUID value as ReadModelCorruptException (→ 500), naming the column")
    void corruptValueBecomesReadModelCorrupt() {
        assertThatThrownBy(() -> ReadModelIds.requireUuid("node-001", "inventory_nodes.id"))
                .isInstanceOf(ReadModelCorruptException.class)
                .hasMessageContaining("inventory_nodes.id")
                .hasMessageContaining("node-001")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requireUuid wraps a null value as ReadModelCorruptException (not a raw NPE)")
    void nullValueBecomesReadModelCorrupt() {
        assertThatThrownBy(() -> ReadModelIds.requireUuid(null, "inventory_snapshots.last_event_id"))
                .isInstanceOf(ReadModelCorruptException.class)
                .hasMessageContaining("inventory_snapshots.last_event_id");
    }
}
