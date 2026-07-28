package com.example.scmplatform.inventoryvisibility.domain.expectation.repository;

import com.example.scmplatform.inventoryvisibility.domain.expectation.InboundExpectation;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;

import java.util.List;

/**
 * Outbound port for the 3PL inbound-expectation sink (ADR-MONO-055 §D4 /
 * TASK-SCM-BE-049).
 */
public interface InboundExpectationRepository {

    /**
     * Idempotency probe (ADR-MONO-055 §D4): true when an expectation already exists
     * for {@code (tenant, poNumber, sku, node)}. A re-confirmed / replayed PO must
     * not double-record. Backstopped by the DB UNIQUE constraint for the race.
     */
    boolean exists(String tenantId, String sourcePoNumber, Sku sku, NodeId nodeId);

    /**
     * All {@code OPEN} expectations for a {@code (node, sku)} pair — the
     * reconciliation lookup used when a 3PL observation arrives (TASK-SCM-BE-047).
     */
    List<InboundExpectation> findOpenByNodeAndSku(NodeId nodeId, Sku sku, String tenantId);

    /** Persist (insert or update) an expectation. */
    InboundExpectation save(InboundExpectation expectation);
}
