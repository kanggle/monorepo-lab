package com.example.product.domain.repository;

import com.example.product.domain.model.Inventory;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {

    Inventory save(Inventory inventory);

    Optional<Inventory> findByVariantId(UUID variantId);
}
