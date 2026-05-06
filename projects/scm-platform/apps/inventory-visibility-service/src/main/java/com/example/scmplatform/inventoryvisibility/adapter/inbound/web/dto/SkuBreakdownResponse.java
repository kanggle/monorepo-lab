package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU cross-node breakdown: node-by-node quantities + total.
 * Acceptance Criteria #15.
 */
public record SkuBreakdownResponse(
        String sku,
        List<NodeQuantity> nodes,
        BigDecimal totalQuantity
) {
    public record NodeQuantity(String nodeId, BigDecimal quantity, String staleness) {}

    public static SkuBreakdownResponse from(String sku, List<InventorySnapshot> snapshots,
                                             java.util.function.Function<String, String> stalenessLookup) {
        List<NodeQuantity> nodes = snapshots.stream()
                .map(s -> new NodeQuantity(
                        s.getNodeId().toString(),
                        s.getQuantity().value(),
                        stalenessLookup.apply(s.getNodeId().toString())))
                .toList();
        BigDecimal total = snapshots.stream()
                .map(s -> s.getQuantity().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SkuBreakdownResponse(sku, nodes, total);
    }
}
