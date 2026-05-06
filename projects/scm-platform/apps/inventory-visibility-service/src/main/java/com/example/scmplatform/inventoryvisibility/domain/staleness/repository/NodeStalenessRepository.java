package com.example.scmplatform.inventoryvisibility.domain.staleness.repository;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.staleness.NodeStaleness;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for NodeStaleness persistence.
 */
public interface NodeStalenessRepository {

    Optional<NodeStaleness> findByNodeId(NodeId nodeId);

    List<NodeStaleness> findAllByTenantId(String tenantId);

    NodeStaleness save(NodeStaleness staleness);
}
