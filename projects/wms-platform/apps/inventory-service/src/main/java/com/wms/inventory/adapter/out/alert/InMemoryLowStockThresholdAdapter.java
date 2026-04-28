package com.wms.inventory.adapter.out.alert;

import com.wms.inventory.application.port.out.LowStockThresholdPort;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link LowStockThresholdPort} for v1.
 *
 * <p>v1 simplification per TASK-BE-024 implementation notes: thresholds are
 * stored in memory and refreshed on a 5-minute TTL via a future
 * {@code admin.settings} subscription. Until that pipeline lands, the
 * in-memory map starts empty (no alerts fire) — populated either by
 * configuration or test setup. The {@code admin-service} integration is
 * deferred and will replace this adapter in-place via Spring profile.
 *
 * <p>Lookup precedence (per spec):
 * <ol>
 *   <li>{@code (warehouseId, skuId)} specific override</li>
 *   <li>Global {@code default} threshold</li>
 *   <li>{@link Optional#empty()} → low-stock detection disabled for this row</li>
 * </ol>
 */
public class InMemoryLowStockThresholdAdapter implements LowStockThresholdPort {

    private final Map<String, Integer> overrides = new ConcurrentHashMap<>();
    private volatile Integer defaultThreshold;

    public void setDefaultThreshold(Integer threshold) {
        this.defaultThreshold = threshold;
    }

    public void setOverride(UUID warehouseId, UUID skuId, int threshold) {
        overrides.put(key(warehouseId, skuId), threshold);
    }

    public void clearAll() {
        overrides.clear();
        defaultThreshold = null;
    }

    @Override
    public Optional<Integer> findThreshold(UUID warehouseId, UUID skuId) {
        Integer override = overrides.get(key(warehouseId, skuId));
        if (override != null) {
            return Optional.of(override);
        }
        return Optional.ofNullable(defaultThreshold);
    }

    private static String key(UUID warehouseId, UUID skuId) {
        return warehouseId + ":" + skuId;
    }
}
