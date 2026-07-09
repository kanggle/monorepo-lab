package com.wms.inventory.adapter.out.alert;

import com.wms.inventory.application.port.out.LowStockThresholdPort;
import com.wms.inventory.application.port.out.LowStockThresholdWriterPort;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory holder backing both the low-stock threshold read port
 * ({@link LowStockThresholdPort}) and write port
 * ({@link LowStockThresholdWriterPort}).
 *
 * <p>TASK-BE-459 (Option B): the global default is bootstrapped from the
 * {@code inventory.alert.low-stock.default-threshold} config property and then
 * updated live by {@code AdminSettingsConsumer} on {@code admin.settings.changed}
 * for {@code inventory.low_stock.default_threshold_qty}. A single instance is
 * exposed as both ports (see {@code AlertConfig}) so the consumer's writes are
 * visible to {@code findThreshold}. Restart-durability of an operator-set value
 * is deferred (would need a startup HTTP read; WMS has no service-to-service
 * HTTP auth yet) — on restart the config default applies until the next event.
 *
 * <p>Lookup precedence (per spec):
 * <ol>
 *   <li>{@code (warehouseId, skuId)} specific override</li>
 *   <li>Global {@code default} threshold</li>
 *   <li>{@link Optional#empty()} → low-stock detection disabled for this row</li>
 * </ol>
 */
public class InMemoryLowStockThresholdAdapter
        implements LowStockThresholdPort, LowStockThresholdWriterPort {

    private final Map<String, Integer> overrides = new ConcurrentHashMap<>();
    private volatile Integer defaultThreshold;

    /** Bootstrap/test setter — same effect as {@link #updateDefaultThreshold}. */
    public void setDefaultThreshold(Integer threshold) {
        this.defaultThreshold = threshold;
    }

    @Override
    public void updateDefaultThreshold(Integer threshold) {
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
