package com.example.scmplatform.inventoryvisibility.domain.node;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root representing a single inventory node in the supply chain.
 * A node can be a wms warehouse, a supplier location, a 3PL warehouse, or an
 * in-transit shipment.
 *
 * <p>v1: WMS_WAREHOUSE nodes are auto-registered when wms inventory events are
 * first received (Edge Case 3 in TASK-SCM-BE-003). Name and contactInfo start
 * empty for auto-registered nodes. THIRD_PARTY_LOGISTICS nodes have no such
 * event stream and are instead **explicitly registered** at onboarding
 * (ADR-MONO-054 §D2 / TASK-SCM-BE-046) — named at registration, observed
 * read-only thereafter, never operated (ADR-054 §D4 / ADR-050 §D4).
 */
public class InventoryNode {

    private final NodeId id;
    private final String tenantId;
    private final String nodeExternalId;
    private NodeType nodeType;
    private String name;
    private NodeStatus status;
    private String contactInfo; // JSONB stored as String
    private String warehouseCode; // nullable — ADR-MONO-050 D9
    private final Instant createdAt;
    private Instant updatedAt;

    public InventoryNode(NodeId id, String tenantId, String nodeExternalId,
                         NodeType nodeType, String name, NodeStatus status,
                         String contactInfo, Instant createdAt, Instant updatedAt) {
        this(id, tenantId, nodeExternalId, nodeType, name, status, contactInfo,
                null, createdAt, updatedAt);
    }

    public InventoryNode(NodeId id, String tenantId, String nodeExternalId,
                         NodeType nodeType, String name, NodeStatus status,
                         String contactInfo, String warehouseCode,
                         Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.nodeExternalId = Objects.requireNonNull(nodeExternalId, "nodeExternalId");
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType");
        this.name = name != null ? name : "";
        this.status = Objects.requireNonNull(status, "status");
        this.contactInfo = contactInfo;
        this.warehouseCode = warehouseCode;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Factory: auto-register a WMS_WAREHOUSE node upon first event receipt.
     * Node name and contactInfo are empty — will be enriched when metadata arrives.
     *
     * @param warehouseCode the business warehouse code carried by the triggering wms
     *                      mutation event (ADR-MONO-050 D9). Nullable — wms emits
     *                      {@code null} while its warehouse master snapshot is
     *                      unpopulated; the node is still registered.
     */
    public static InventoryNode autoRegisterWmsWarehouse(NodeId id, String tenantId,
                                                          String warehouseId,
                                                          String warehouseCode, Instant now) {
        return new InventoryNode(id, tenantId, warehouseId,
                NodeType.WMS_WAREHOUSE, "", NodeStatus.ACTIVE,
                null, warehouseCode, now, now);
    }

    /**
     * Factory: explicitly register a THIRD_PARTY_LOGISTICS node (ADR-MONO-054 §D2 /
     * TASK-SCM-BE-046). Unlike {@link #autoRegisterWmsWarehouse}, a 3PL node has no
     * upstream event stream to be born from — a 3PL relationship is an onboarding
     * decision, so it is named and activated at registration time rather than
     * enriched later. {@code warehouseCode} is left {@code null}: it is a wms-only
     * business code (ADR-MONO-050 §D9) that never applies to a 3PL node.
     *
     * @param name the 3PL relationship's display name, known at onboarding (contrast
     *             the auto-registered warehouse node, whose name starts empty)
     */
    public static InventoryNode registerThirdPartyLogistics(NodeId id, String tenantId,
                                                             String nodeExternalId,
                                                             String name, Instant now) {
        return new InventoryNode(id, tenantId, nodeExternalId,
                NodeType.THIRD_PARTY_LOGISTICS, name, NodeStatus.ACTIVE,
                null, null, now, now);
    }

    /**
     * Set-if-present update of the warehouse code (ADR-MONO-050 D9).
     *
     * <p>A {@code null} incoming code is ignored so a best-effort-null wms event
     * can never wipe a previously learned non-null code. Returns {@code true} when
     * the stored value actually changed, so the caller can skip a redundant write.
     */
    public boolean applyWarehouseCodeIfPresent(String incomingWarehouseCode, Instant now) {
        if (incomingWarehouseCode == null || incomingWarehouseCode.equals(this.warehouseCode)) {
            return false;
        }
        this.warehouseCode = incomingWarehouseCode;
        this.updatedAt = Objects.requireNonNull(now, "now");
        return true;
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
    /** Nullable business warehouse code learned from wms mutation events (ADR-MONO-050 D9). */
    public String getWarehouseCode() { return warehouseCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
