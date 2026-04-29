package com.wms.inbound.adapter.out.idempotency;

import com.wms.inbound.application.port.out.IdempotencyStore;
import com.wms.inbound.application.port.out.StoredResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link IdempotencyStore} for the {@code standalone}
 * profile and tests. Entries expire lazily on access.
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
        // compute() is atomic per-key: the lambda runs without concurrent interference.
        Long result = locks.compute(storageKey, (k, existing) -> {
            if (existing != null && existing > now) {
                return existing; // lock is held — do not overwrite
            }
            return expiresAt; // acquire or renew expired lock
        });
        return result.longValue() == expiresAt;
    }

    @Override
    public void releaseLock(String storageKey) {
        locks.remove(storageKey);
    }

    private record Entry(StoredResponse response, Instant expiresAt) {
    }
}
