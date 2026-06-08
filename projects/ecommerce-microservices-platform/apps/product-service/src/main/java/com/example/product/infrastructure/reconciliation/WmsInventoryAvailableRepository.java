package com.example.product.infrastructure.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Per-inventoryId availableQty trajectory ledger (ADR-MONO-022 §D4 v2(b)). */
public interface WmsInventoryAvailableRepository extends JpaRepository<WmsInventoryAvailableEntity, UUID> {
}
