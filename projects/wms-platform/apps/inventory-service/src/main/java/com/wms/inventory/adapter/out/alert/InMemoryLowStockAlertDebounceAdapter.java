package com.wms.inventory.adapter.out.alert;

import com.wms.inventory.application.port.out.LowStockAlertDebouncePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory debounce with a fixed 1h window. Used under the
 * {@code standalone} profile and in tests.
 */
public class InMemoryLowStockAlertDebounceAdapter implements LowStockAlertDebouncePort {

    private static final Duration TTL = Duration.ofHours(1);

    private final Map<UUID, Instant> firedAt = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryLowStockAlertDebounceAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean shouldFire(UUID inventoryId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(TTL);
        // computeIfPresent removes the stale entry if expired, otherwise keeps it.
        boolean[] fired = {false};
        firedAt.compute(inventoryId, (k, prev) -> {
            if (prev == null || prev.isBefore(cutoff)) {
                fired[0] = true;
                return now;
            }
            return prev;
        });
        return fired[0];
    }

    @Override
    public void clear(UUID inventoryId) {
        firedAt.remove(inventoryId);
    }
}
