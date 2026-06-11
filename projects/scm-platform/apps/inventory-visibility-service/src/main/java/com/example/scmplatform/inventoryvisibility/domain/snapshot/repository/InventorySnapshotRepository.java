package com.example.scmplatform.inventoryvisibility.domain.snapshot.repository;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.SnapshotId;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for InventorySnapshot persistence.
 */
public interface InventorySnapshotRepository {

    Optional<InventorySnapshot> findById(SnapshotId id);

    Optional<InventorySnapshot> findByNodeIdAndSku(NodeId nodeId, Sku sku, String tenantId);

    List<InventorySnapshot> findByNodeId(NodeId nodeId, String tenantId);

    List<InventorySnapshot> findBySku(Sku sku, String tenantId);

    List<InventorySnapshot> findAll(String tenantId, int page, int size);

    /**
     * Cross-tenant snapshot read for the demand-planning replenishment batch
     * (ADR-MONO-027 §D7.1) — the batch is tenant-agnostic and reads via the
     * internal network-trusted endpoint. NOT used by the authenticated `/api`
     * surface (which is always tenant-scoped).
     */
    List<InventorySnapshot> findAllAcrossTenants();

    long countAll(String tenantId);

    InventorySnapshot save(InventorySnapshot snapshot);
}
