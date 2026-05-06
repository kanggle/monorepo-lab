package com.example.scmplatform.inventoryvisibility.domain.staleness;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeStalenessTest {

    private final NodeId nodeId = NodeId.of(UUID.randomUUID());
    private final StalenessThreshold threshold = StalenessThreshold.ofSeconds(600); // 10 min
    private final Instant baseTime = Instant.parse("2026-05-01T10:00:00Z");

    @Test
    void create_initialStatus_isFresh() {
        NodeStaleness ns = NodeStaleness.create(nodeId, "scm", baseTime, UUID.randomUUID());
        assertThat(ns.getStalenessStatus()).isEqualTo(StalenessStatus.FRESH);
    }

    @Test
    void evaluate_withinThreshold_remainsFresh() {
        NodeStaleness ns = NodeStaleness.create(nodeId, "scm", baseTime, UUID.randomUUID());
        Instant now = baseTime.plus(5, ChronoUnit.MINUTES); // 5 min < 10 min threshold

        boolean changed = ns.evaluate(threshold, now);

        assertThat(ns.getStalenessStatus()).isEqualTo(StalenessStatus.FRESH);
        assertThat(changed).isFalse(); // was already FRESH
    }

    @Test
    void evaluate_beyondThreshold_becomesStale_andReturnsChanged() {
        NodeStaleness ns = NodeStaleness.create(nodeId, "scm", baseTime, UUID.randomUUID());
        Instant now = baseTime.plus(11, ChronoUnit.MINUTES); // 11 min > 10 min threshold

        boolean changed = ns.evaluate(threshold, now);

        assertThat(ns.getStalenessStatus()).isEqualTo(StalenessStatus.STALE);
        assertThat(changed).isTrue();
    }

    @Test
    void evaluate_alreadyStale_noRepeatChange() {
        NodeStaleness ns = NodeStaleness.create(nodeId, "scm", baseTime, UUID.randomUUID());
        Instant staleTime = baseTime.plus(11, ChronoUnit.MINUTES);
        ns.evaluate(threshold, staleTime); // first: FRESH → STALE

        boolean changedAgain = ns.evaluate(threshold, staleTime.plus(5, ChronoUnit.MINUTES));

        assertThat(ns.getStalenessStatus()).isEqualTo(StalenessStatus.STALE);
        assertThat(changedAgain).isFalse(); // already STALE, no new transition
    }

    @Test
    void recordEventReceived_resetsToFresh() {
        NodeStaleness ns = NodeStaleness.create(nodeId, "scm", baseTime, UUID.randomUUID());
        Instant staleTime = baseTime.plus(11, ChronoUnit.MINUTES);
        ns.evaluate(threshold, staleTime); // STALE
        assertThat(ns.getStalenessStatus()).isEqualTo(StalenessStatus.STALE);

        UUID newEventId = UUID.randomUUID();
        Instant eventTime = staleTime.plus(1, ChronoUnit.MINUTES);
        ns.recordEventReceived(newEventId, eventTime);

        assertThat(ns.getStalenessStatus()).isEqualTo(StalenessStatus.FRESH);
        assertThat(ns.getLastEventAt()).isEqualTo(eventTime);
        assertThat(ns.getLastEventId()).isEqualTo(newEventId);
    }

    @Test
    void evaluate_noLastEventAt_becomesUnreachable() {
        // Node with null lastEventAt = never received events
        NodeStaleness ns = new NodeStaleness(nodeId, "scm", null, null,
                StalenessStatus.FRESH, null);

        boolean changed = ns.evaluate(threshold, baseTime);

        assertThat(ns.getStalenessStatus()).isEqualTo(StalenessStatus.UNREACHABLE);
        assertThat(changed).isTrue();
    }

    @Test
    void isStale_returnsTrue_forStaleAndUnreachable() {
        NodeStaleness staleNs = new NodeStaleness(nodeId, "scm", baseTime, null,
                StalenessStatus.STALE, baseTime);
        NodeStaleness unreachableNs = new NodeStaleness(nodeId, "scm", null, null,
                StalenessStatus.UNREACHABLE, baseTime);
        NodeStaleness freshNs = new NodeStaleness(nodeId, "scm", baseTime, null,
                StalenessStatus.FRESH, baseTime);

        assertThat(staleNs.isStale()).isTrue();
        assertThat(unreachableNs.isStale()).isTrue();
        assertThat(freshNs.isStale()).isFalse();
    }
}
