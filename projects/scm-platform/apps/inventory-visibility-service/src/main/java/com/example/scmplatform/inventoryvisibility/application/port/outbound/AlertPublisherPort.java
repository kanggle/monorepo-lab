package com.example.scmplatform.inventoryvisibility.application.port.outbound;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.staleness.StalenessStatus;

import java.time.Instant;

/**
 * Outbound port for publishing inventory alert events.
 * <p>
 * v1: publishes to {@code scm.inventory.alert.v1} topic.
 * Best-effort (no outbox) — alert loss acceptable because the next batch
 * (5 minutes) re-detects and re-publishes (Failure Scenario H).
 */
public interface AlertPublisherPort {

    /**
     * Publish a SNAPSHOT_STALE or NODE_UNREACHABLE alert for a node.
     */
    void publishStalenessAlert(NodeId nodeId, String tenantId,
                                StalenessStatus status, Instant detectedAt);
}
