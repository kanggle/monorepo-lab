package com.example.scmplatform.inventoryvisibility.domain.node.repository;

import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for InventoryNode persistence.
 * Implementations live in the outbound persistence adapter.
 */
public interface InventoryNodeRepository {

    Optional<InventoryNode> findById(NodeId id);

    Optional<InventoryNode> findByTenantIdAndExternalId(String tenantId, String externalId);

    List<InventoryNode> findAllByTenantId(String tenantId);

    InventoryNode save(InventoryNode node);
}
