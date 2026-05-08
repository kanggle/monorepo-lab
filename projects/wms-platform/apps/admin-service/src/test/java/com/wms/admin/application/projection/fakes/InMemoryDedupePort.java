package com.wms.admin.application.projection.fakes;

import com.wms.admin.application.port.AdminEventDedupePort;
import com.wms.admin.application.projection.DedupeOutcome;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Hand-coded fake port for projection unit tests. */
public class InMemoryDedupePort implements AdminEventDedupePort {

    private final Set<UUID> seen = new HashSet<>();
    private final Map<UUID, String> outcomes = new HashMap<>();
    private long applied = 0;
    private long duplicate = 0;
    private long late = 0;

    @Override
    public DedupeOutcome tryRecord(UUID eventId, String eventType) {
        if (!seen.add(eventId)) {
            duplicate++;
            return DedupeOutcome.DUPLICATE;
        }
        outcomes.put(eventId, DedupeOutcome.APPLIED.name());
        applied++;
        return DedupeOutcome.APPLIED;
    }

    @Override
    public void markStale(UUID eventId) {
        outcomes.put(eventId, DedupeOutcome.IGNORED_DUPLICATE_LATE.name());
        late++;
    }

    @Override
    public LifetimeCounts countLifetime() {
        return new LifetimeCounts(applied, duplicate, late, 0);
    }

    public boolean isStale(UUID eventId) {
        return DedupeOutcome.IGNORED_DUPLICATE_LATE.name().equals(outcomes.get(eventId));
    }
}
