package com.example.scmplatform.inventoryvisibility.domain.staleness;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks the freshness state of a specific inventory node.
 * One record per node — updated whenever a Kafka event is processed for that node.
 * <p>
 * The staleness detection batch (every 5 minutes) evaluates all nodes by
 * comparing {@code lastEventAt} against {@code StalenessThreshold}.
 */
public class NodeStaleness {

    private final NodeId nodeId;
    private final String tenantId;
    private Instant lastEventAt;
    private UUID lastEventId;
    private StalenessStatus stalenessStatus;
    private Instant lastCheckedAt;

    public NodeStaleness(NodeId nodeId, String tenantId, Instant lastEventAt,
                         UUID lastEventId, StalenessStatus stalenessStatus, Instant lastCheckedAt) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
        this.stalenessStatus = Objects.requireNonNull(stalenessStatus, "stalenessStatus");
        this.lastCheckedAt = lastCheckedAt;
    }

    public static NodeStaleness create(NodeId nodeId, String tenantId, Instant eventAt, UUID eventId) {
        return new NodeStaleness(nodeId, tenantId, eventAt, eventId, StalenessStatus.FRESH, eventAt);
    }

    /**
     * Record a new event received for this node, resetting status to FRESH.
     */
    public void recordEventReceived(UUID eventId, Instant eventAt) {
        this.lastEventAt = Objects.requireNonNull(eventAt, "eventAt");
        this.lastEventId = Objects.requireNonNull(eventId, "eventId");
        this.stalenessStatus = StalenessStatus.FRESH;
    }

    /**
     * Evaluate current staleness based on the threshold and current time.
     *
     * @return true if the status changed (useful for alert dedup — only publish
     *         SNAPSHOT_STALE when transitioning into STALE, not on every check).
     */
    public boolean evaluate(StalenessThreshold threshold, Instant now) {
        this.lastCheckedAt = now;
        if (lastEventAt == null) {
            boolean changed = stalenessStatus != StalenessStatus.UNREACHABLE;
            this.stalenessStatus = StalenessStatus.UNREACHABLE;
            return changed;
        }
        Duration elapsed = Duration.between(lastEventAt, now);
        if (elapsed.compareTo(threshold.value()) > 0) {
            boolean changed = stalenessStatus != StalenessStatus.STALE;
            this.stalenessStatus = StalenessStatus.STALE;
            return changed;
        } else {
            boolean changed = stalenessStatus != StalenessStatus.FRESH;
            this.stalenessStatus = StalenessStatus.FRESH;
            return changed;
        }
    }

    public boolean isStale() {
        return stalenessStatus == StalenessStatus.STALE
                || stalenessStatus == StalenessStatus.UNREACHABLE;
    }

    // Getters
    public NodeId getNodeId() { return nodeId; }
    public String getTenantId() { return tenantId; }
    public Instant getLastEventAt() { return lastEventAt; }
    public UUID getLastEventId() { return lastEventId; }
    public StalenessStatus getStalenessStatus() { return stalenessStatus; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
}
