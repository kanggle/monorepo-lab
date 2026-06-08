package com.example.product.domain.repository;

import com.example.product.domain.model.Inventory;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {

    Inventory save(Inventory inventory);

    Optional<Inventory> findByVariantId(UUID variantId);

    /**
     * Resolves a variant by its SKU business key (== wms {@code skuCode}) — the
     * reverse-identity hop for wms inventory reconciliation (ADR-MONO-022 §D4 v2(b)).
     * Carries the productId + current stock the reconciliation needs (for the emitted
     * {@code product.product.stock-changed} and the underflow clamp) in one read.
     */
    Optional<VariantRef> findVariantBySku(String sku);

    record VariantRef(UUID variantId, UUID productId, int currentStock) {}
}
