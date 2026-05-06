package com.example.scmplatform.inventoryvisibility.domain.snapshot;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-model: quantity of a specific SKU at a specific node.
 * <p>
 * Mutated by Kafka event consumers only (no REST mutations).
 * Version field enables optimistic locking (Acceptance Criteria #10 atomicity).
 * <p>
 * S5: this is an eventual-consistency read-model — callers should check
 * {@code lastEventAt} staleness before trusting quantity values for critical decisions.
 */
public class InventorySnapshot {

    private final SnapshotId id;
    private final NodeId nodeId;
    private final Sku sku;
    private final String tenantId;
    private Quantity quantity;
    private UUID lastEventId;
    private Instant lastEventAt;
    private int version;
    private Instant updatedAt;

    public InventorySnapshot(SnapshotId id, NodeId nodeId, Sku sku, String tenantId,
                              Quantity quantity, UUID lastEventId, Instant lastEventAt,
                              int version, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.lastEventId = Objects.requireNonNull(lastEventId, "lastEventId");
        this.lastEventAt = Objects.requireNonNull(lastEventAt, "lastEventAt");
        this.version = version;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static InventorySnapshot create(NodeId nodeId, Sku sku, String tenantId,
                                            Quantity quantity, UUID eventId, Instant eventAt) {
        return new InventorySnapshot(
                SnapshotId.generate(), nodeId, sku, tenantId,
                quantity, eventId, eventAt, 0, eventAt);
    }

    /**
     * Apply a new absolute quantity from an event. Increments version.
     */
    public void applyQuantity(Quantity newQuantity, UUID eventId, Instant eventAt) {
        this.quantity = Objects.requireNonNull(newQuantity, "newQuantity");
        this.lastEventId = Objects.requireNonNull(eventId, "eventId");
        this.lastEventAt = Objects.requireNonNull(eventAt, "eventAt");
        this.version++;
        this.updatedAt = eventAt;
    }

    /**
     * Apply a delta (positive = add, negative quantities clamped to zero).
     */
    public void applyDelta(Quantity delta, boolean isAddition, UUID eventId, Instant eventAt) {
        if (isAddition) {
            this.quantity = this.quantity.add(delta);
        } else {
            this.quantity = this.quantity.subtract(delta);
        }
        this.lastEventId = Objects.requireNonNull(eventId, "eventId");
        this.lastEventAt = Objects.requireNonNull(eventAt, "eventAt");
        this.version++;
        this.updatedAt = eventAt;
    }

    // Getters
    public SnapshotId getId() { return id; }
    public NodeId getNodeId() { return nodeId; }
    public Sku getSku() { return sku; }
    public String getTenantId() { return tenantId; }
    public Quantity getQuantity() { return quantity; }
    public UUID getLastEventId() { return lastEventId; }
    public Instant getLastEventAt() { return lastEventAt; }
    public int getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
