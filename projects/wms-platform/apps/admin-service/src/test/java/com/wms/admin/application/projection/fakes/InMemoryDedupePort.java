package com.wms.admin.application.projection.fakes;

import com.wms.admin.application.repository.AdminEventDedupeRepository;
import com.wms.admin.application.projection.DedupeOutcome;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Hand-coded fake port for projection unit tests. */
public class InMemoryDedupePort implements AdminEventDedupeRepository {

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

    @Override
    public Map<String, Instant> maxProcessedAtByEventType(Collection<String> eventTypes) {
        Map<String, Instant> out = new LinkedHashMap<>();
        for (Map.Entry<String, Instant> entry : eventTypeWatermarks.entrySet()) {
            if (eventTypes.contains(entry.getKey())) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    public void recordWatermark(String eventType, Instant processedAt) {
        eventTypeWatermarks.put(eventType, processedAt);
    }

    private final Map<String, Instant> eventTypeWatermarks = new HashMap<>();

    public boolean isStale(UUID eventId) {
        return DedupeOutcome.IGNORED_DUPLICATE_LATE.name().equals(outcomes.get(eventId));
    }
}
