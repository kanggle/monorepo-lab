package com.wms.outbound.adapter.out.idempotency;

import com.wms.outbound.application.port.out.IdempotencyStore;
import com.wms.outbound.application.port.out.StoredResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link IdempotencyStore} for the {@code standalone}
 * profile and tests.
 *
 * <p><b>Atomicity:</b> {@link #tryAcquireLock(String, Duration)} uses
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} so
 * the lock check and replacement happen under the same per-key lock owned by
 * {@link ConcurrentHashMap}. This eliminates the get-then-put race window
 * present in earlier inbound/inventory implementations (mirrors the
 * inbound-service TASK-BE-033 fix).
 *
 * <p>The lock value is stored as a primitive {@code long} (epoch millis of
 * expiry). Comparisons inside {@code compute()} use {@code <} on primitives —
 * never {@code Long.equals()} — to sidestep the autoboxing-cache trap.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, Long> locks = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryIdempotencyStore() {
        this(Clock.systemUTC());
    }

    public InMemoryIdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<StoredResponse> lookup(String storageKey) {
        Entry entry = entries.get(storageKey);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.instant().isAfter(entry.expiresAt)) {
            entries.remove(storageKey, entry);
            return Optional.empty();
        }
        return Optional.of(entry.response);
    }

    @Override
    public void put(String storageKey, StoredResponse response, Duration ttl) {
        entries.put(storageKey, new Entry(response, clock.instant().plus(ttl)));
    }

    @Override
    public boolean tryAcquireLock(String storageKey, Duration ttl) {
        long now = clock.millis();
        long expiresAt = now + ttl.toMillis();
        boolean[] acquired = {false};
        locks.compute(storageKey, (key, existing) -> {
            if (existing != null && existing.longValue() > now) {
                // Active lock held by someone else.
                acquired[0] = false;
                return existing;
            }
            // No lock or expired lock — take it.
            acquired[0] = true;
            return expiresAt;
        });
        return acquired[0];
    }

    @Override
    public void releaseLock(String storageKey) {
        locks.remove(storageKey);
    }

    private record Entry(StoredResponse response, Instant expiresAt) {
    }
}
