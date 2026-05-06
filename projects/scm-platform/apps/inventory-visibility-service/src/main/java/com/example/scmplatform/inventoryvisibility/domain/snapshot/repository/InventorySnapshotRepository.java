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

    long countAll(String tenantId);

    InventorySnapshot save(InventorySnapshot snapshot);
}
