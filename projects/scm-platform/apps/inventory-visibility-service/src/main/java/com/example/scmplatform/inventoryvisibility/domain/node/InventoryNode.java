package com.example.scmplatform.inventoryvisibility.domain.node;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root representing a single inventory node in the supply chain.
 * A node can be a wms warehouse, a supplier location, a 3PL warehouse, or an
 * in-transit shipment.
 *
 * <p>v1: nodes are auto-registered when wms inventory events are first received
 * (Edge Case 3 in TASK-SCM-BE-003). Name and contactInfo start empty for
 * auto-registered nodes.
 */
public class InventoryNode {

    private final NodeId id;
    private final String tenantId;
    private final String nodeExternalId;
    private NodeType nodeType;
    private String name;
    private NodeStatus status;
    private String contactInfo; // JSONB stored as String
    private final Instant createdAt;
    private Instant updatedAt;

    public InventoryNode(NodeId id, String tenantId, String nodeExternalId,
                         NodeType nodeType, String name, NodeStatus status,
                         String contactInfo, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.nodeExternalId = Objects.requireNonNull(nodeExternalId, "nodeExternalId");
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType");
        this.name = name != null ? name : "";
        this.status = Objects.requireNonNull(status, "status");
        this.contactInfo = contactInfo;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Factory: auto-register a WMS_WAREHOUSE node upon first event receipt.
     * Node name and contactInfo are empty — will be enriched when metadata arrives.
     */
    public static InventoryNode autoRegisterWmsWarehouse(NodeId id, String tenantId,
                                                          String warehouseId, Instant now) {
        return new InventoryNode(id, tenantId, warehouseId,
                NodeType.WMS_WAREHOUSE, "", NodeStatus.ACTIVE,
                null, now, now);
    }

    public boolean isActive() {
        return NodeStatus.ACTIVE == status;
    }

    // Getters (no setters — mutate via explicit methods)
    public NodeId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getNodeExternalId() { return nodeExternalId; }
    public NodeType getNodeType() { return nodeType; }
    public String getName() { return name; }
    public NodeStatus getStatus() { return status; }
    public String getContactInfo() { return contactInfo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
