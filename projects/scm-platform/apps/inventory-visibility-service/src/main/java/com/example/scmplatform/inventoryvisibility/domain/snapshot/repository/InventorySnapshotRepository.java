package com.example.scmplatform.inventoryvisibility.domain.snapshot.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
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

    /**
     * Cross-node paginated search for the given tenant, sourced from the shared
     * {@link PageQuery}/{@link PageResult} carrier (ADR-MONO-058 § D3 / TASK-SCM-BE-056
     * — mirrors {@code procurement-service}'s {@code PurchaseOrderRepository.search}
     * pattern). Replaces the previous hand-rolled {@code findAll(tenantId, page, size)}
     * + {@code countAll(tenantId)} pair (two separate queries with no {@code totalPages}).
     */
    PageResult<InventorySnapshot> search(String tenantId, PageQuery pageQuery);

    /**
     * Cross-tenant snapshot read for the demand-planning replenishment batch
     * (ADR-MONO-027 §D7.1) — the batch is tenant-agnostic and reads via the
     * internal network-trusted endpoint. NOT used by the authenticated `/api`
     * surface (which is always tenant-scoped).
     */
    List<InventorySnapshot> findAllAcrossTenants();

    InventorySnapshot save(InventorySnapshot snapshot);
}
