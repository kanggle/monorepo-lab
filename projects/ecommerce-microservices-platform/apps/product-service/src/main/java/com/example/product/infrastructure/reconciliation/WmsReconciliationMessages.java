package com.example.product.infrastructure.reconciliation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Inbound wms event DTOs (camelCase envelope) for §D4 v2(b) reconciliation. Only the
 * fields product-service reads are mapped; everything else is ignored. Authoritative
 * schemas: wms {@code inventory-events.md} + {@code master-events.md}.
 */
public final class WmsReconciliationMessages {

    private WmsReconciliationMessages() {
    }

    /** {@code wms.master.sku.v1} — reverse-identity stream (skuId ↔ skuCode). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MasterSkuMessage(String eventId, String eventType, MasterSkuPayload payload) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record MasterSkuPayload(Sku sku) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Sku(String id, String skuCode, long version) {}
    }

    /** {@code wms.inventory.received.v1} — putaway restock (+ available per line). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InventoryReceivedMessage(String eventId, String eventType, ReceivedPayload payload) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ReceivedPayload(List<ReceivedLine> lines) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ReceivedLine(String inventoryId, String skuId, int availableQtyAfter) {}
    }

    /** {@code wms.inventory.adjusted.v1} — manual / damage / loss (post-snapshot availableQty). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InventoryAdjustedMessage(String eventId, String eventType, AdjustedPayload payload) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record AdjustedPayload(String inventoryId, String skuId, InventorySnapshot inventory) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record InventorySnapshot(int availableQty) {}
    }
}
