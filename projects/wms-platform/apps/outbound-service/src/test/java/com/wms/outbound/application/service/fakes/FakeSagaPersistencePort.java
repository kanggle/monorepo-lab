package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeSagaPersistencePort implements SagaPersistencePort {

    private final Map<UUID, OutboundSaga> store = new HashMap<>();

    /**
     * Counter incremented every time {@link #findByOrderId} is called. Tests
     * use this to assert the list endpoint does not fall back to per-row
     * lookups (AC-03).
     */
    public int findByOrderIdCallCount;

    /**
     * Clock used by {@link #findStuck} to compute the staleness threshold.
     * Tests can substitute a fixed clock to deterministically position
     * stuck sagas. Defaults to {@link Clock#systemUTC()} so existing tests
     * that don't exercise the sweeper continue to work unchanged.
     */
    public Clock clock = Clock.systemUTC();

    @Override
    public OutboundSaga save(OutboundSaga saga) {
        store.put(saga.sagaId(), saga);
        return saga;
    }

    @Override
    public Optional<OutboundSaga> findById(UUID sagaId) {
        return Optional.ofNullable(store.get(sagaId));
    }

    @Override
    public Optional<OutboundSaga> findByOrderId(UUID orderId) {
        findByOrderIdCallCount++;
        return store.values().stream()
                .filter(s -> s.orderId().equals(orderId))
                .findFirst();
    }

    @Override
    public Optional<OutboundSaga> findByPickingRequestId(UUID pickingRequestId) {
        return store.values().stream()
                .filter(s -> pickingRequestId.equals(s.pickingRequestId()))
                .findFirst();
    }

    @Override
    public List<OutboundSaga> findStuck(SagaStatus status, Duration gracePeriod, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        java.time.Instant threshold = clock.instant().minus(
                gracePeriod == null ? Duration.ZERO : gracePeriod);
        List<OutboundSaga> candidates = new ArrayList<>();
        for (OutboundSaga s : store.values()) {
            if (s.status() == status && s.lastTransitionAt().isBefore(threshold)) {
                candidates.add(s);
            }
        }
        candidates.sort(Comparator.comparing(OutboundSaga::lastTransitionAt));
        return candidates.size() <= limit
                ? candidates
                : new ArrayList<>(candidates.subList(0, limit));
    }

    @Override
    public Map<UUID, String> findSagaStatesByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        for (OutboundSaga s : store.values()) {
            if (orderIds.contains(s.orderId())) {
                out.put(s.orderId(), s.status().name());
            }
        }
        return out;
    }

    public int sagaCount() {
        return store.size();
    }
}
