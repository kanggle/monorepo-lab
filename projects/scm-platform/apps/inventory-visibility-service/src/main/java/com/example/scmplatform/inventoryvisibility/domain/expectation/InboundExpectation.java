package com.example.scmplatform.inventoryvisibility.domain.expectation;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The scm-internal <b>3PL inbound-expectation</b> read-model record
 * (ADR-MONO-055 §D4 / TASK-SCM-BE-049).
 *
 * <p>Materialized from {@code scm.procurement.inbound-expected.third-party.v1}
 * (published by {@code procurement-service} when a {@code THIRD_PARTY_LOGISTICS}
 * -addressed replenishment PO is confirmed): we <em>expect</em> stock to arrive
 * at a 3PL node we do not operate (ADR-MONO-050 §D4). It is a lightweight
 * projection — one row per PO line — reconciled by a later 3PL observation
 * (TASK-SCM-BE-047), not a domain state machine.
 *
 * <p>Framework-free (pure domain, per the hexagonal boundary): no Spring / JPA
 * annotations here — persistence lives in the JPA adapter.
 */
public class InboundExpectation {

    private final String id;
    private final String tenantId;
    private final NodeId nodeId;
    private final Sku sku;
    private final Quantity expectedQuantity;
    private final String sourcePoId;
    private final String sourcePoNumber;
    private final LocalDate expectedAt; // nullable — null when the PO lead time was unknown
    private ExpectationStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant satisfiedAt; // nullable — set on reconciliation

    public InboundExpectation(String id, String tenantId, NodeId nodeId, Sku sku,
                              Quantity expectedQuantity, String sourcePoId, String sourcePoNumber,
                              LocalDate expectedAt, ExpectationStatus status,
                              Instant createdAt, Instant updatedAt, Instant satisfiedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.expectedQuantity = Objects.requireNonNull(expectedQuantity, "expectedQuantity");
        this.sourcePoId = Objects.requireNonNull(sourcePoId, "sourcePoId");
        this.sourcePoNumber = Objects.requireNonNull(sourcePoNumber, "sourcePoNumber");
        this.expectedAt = expectedAt;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.satisfiedAt = satisfiedAt;
    }

    /** Factory: record a new OPEN expectation for one PO line. */
    public static InboundExpectation record(String tenantId, NodeId nodeId, Sku sku,
                                            Quantity expectedQuantity, String sourcePoId,
                                            String sourcePoNumber, LocalDate expectedAt, Instant now) {
        return new InboundExpectation(
                UUID.randomUUID().toString(), tenantId, nodeId, sku, expectedQuantity,
                sourcePoId, sourcePoNumber, expectedAt, ExpectationStatus.OPEN, now, now, null);
    }

    /** Reconstruct from persistence. */
    public static InboundExpectation reconstitute(String id, String tenantId, NodeId nodeId, Sku sku,
                                                  Quantity expectedQuantity, String sourcePoId,
                                                  String sourcePoNumber, LocalDate expectedAt,
                                                  ExpectationStatus status, Instant createdAt,
                                                  Instant updatedAt, Instant satisfiedAt) {
        return new InboundExpectation(id, tenantId, nodeId, sku, expectedQuantity, sourcePoId,
                sourcePoNumber, expectedAt, status, createdAt, updatedAt, satisfiedAt);
    }

    public boolean isOpen() {
        return status == ExpectationStatus.OPEN;
    }

    /**
     * v1 <b>binary</b> reconciliation rule (ADR-MONO-055 §D4 / TASK-SCM-BE-049 Edge
     * Case — partial-satisfy vs binary): the expectation is satisfied when an
     * observed absolute quantity for the same {@code (node, sku)} meets or exceeds
     * the expected quantity. A partial observation below the expected quantity leaves
     * it {@code OPEN} — an aging operational signal, not a silent close.
     */
    public boolean isSatisfiedBy(Quantity observedQuantity) {
        return observedQuantity.value().compareTo(expectedQuantity.value()) >= 0;
    }

    /** Mark this expectation SATISFIED (idempotent — a second call is a no-op). */
    public void markSatisfied(Instant now) {
        if (status == ExpectationStatus.SATISFIED) {
            return;
        }
        this.status = ExpectationStatus.SATISFIED;
        this.satisfiedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public NodeId getNodeId() { return nodeId; }
    public Sku getSku() { return sku; }
    public Quantity getExpectedQuantity() { return expectedQuantity; }
    public String getSourcePoId() { return sourcePoId; }
    public String getSourcePoNumber() { return sourcePoNumber; }
    public LocalDate getExpectedAt() { return expectedAt; }
    public ExpectationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSatisfiedAt() { return satisfiedAt; }
}
