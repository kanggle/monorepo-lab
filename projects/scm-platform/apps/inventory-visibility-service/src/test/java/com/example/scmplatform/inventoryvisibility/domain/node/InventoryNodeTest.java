package com.example.scmplatform.inventoryvisibility.domain.node;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link InventoryNode#registerThirdPartyLogistics} (ADR-MONO-054 §D2 /
 * TASK-SCM-BE-046) — the explicit 3PL node factory, sibling to
 * {@link InventoryNodeWarehouseCodeTest}'s coverage of {@code autoRegisterWmsWarehouse}.
 */
class InventoryNodeTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

    @Test
    void registerThirdPartyLogistics_producesActive3plNodeWithGivenNameAndNullWarehouseCode() {
        NodeId id = NodeId.of(UUID.randomUUID());

        InventoryNode node = InventoryNode.registerThirdPartyLogistics(
                id, "scm", "3PL-EXT-1", "품고 물류센터", NOW);

        assertThat(node.getId()).isEqualTo(id);
        assertThat(node.getTenantId()).isEqualTo("scm");
        assertThat(node.getNodeExternalId()).isEqualTo("3PL-EXT-1");
        assertThat(node.getNodeType()).isEqualTo(NodeType.THIRD_PARTY_LOGISTICS);
        assertThat(node.getStatus()).isEqualTo(NodeStatus.ACTIVE);
        assertThat(node.isActive()).isTrue();
        assertThat(node.getName()).isEqualTo("품고 물류센터");
        assertThat(node.getWarehouseCode()).isNull();
        assertThat(node.getContactInfo()).isNull();
        assertThat(node.getCreatedAt()).isEqualTo(NOW);
        assertThat(node.getUpdatedAt()).isEqualTo(NOW);
    }

    /** Contrast with auto-registered warehouse nodes, whose name starts empty. */
    @Test
    void registerThirdPartyLogistics_unlikeAutoRegisteredWarehouse_isNamedAtRegistration() {
        InventoryNode warehouse = InventoryNode.autoRegisterWmsWarehouse(
                NodeId.of(UUID.randomUUID()), "scm", "WH-EXT-1", null, NOW);
        InventoryNode thirdParty = InventoryNode.registerThirdPartyLogistics(
                NodeId.of(UUID.randomUUID()), "scm", "3PL-EXT-1", "ShipBob", NOW);

        assertThat(warehouse.getName()).isEmpty();
        assertThat(thirdParty.getName()).isEqualTo("ShipBob");
    }
}
