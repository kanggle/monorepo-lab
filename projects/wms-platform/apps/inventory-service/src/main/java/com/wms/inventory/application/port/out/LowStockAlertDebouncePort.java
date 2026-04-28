package com.wms.inventory.application.port.out;

import java.util.UUID;

/**
 * Per-inventory debounce gate for low-stock alerts. Default impl uses Redis
 * with a 1h TTL key; the in-memory variant for {@code standalone} mirrors the
 * same semantics in-process.
 *
 * <p>{@link #shouldFire(UUID)} returns {@code true} only when no key exists
 * for the supplied inventory id — and atomically sets the key so concurrent
 * mutations don't both fire. {@link #clear(UUID)} is exposed for tests.
 *
 * <p>If the underlying store is unavailable, implementations must
 * <strong>fail open</strong> per the failure-scenarios spec — i.e., return
 * {@code true} so the alert fires (duplicate alerts > silent drops).
 */
public interface LowStockAlertDebouncePort {

    boolean shouldFire(UUID inventoryId);

    void clear(UUID inventoryId);
}
