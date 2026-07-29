package com.wms.master.adapter.out.idempotency;

import com.example.web.idempotency.IdempotencyStore;
import com.example.web.idempotency.StoredResponse;
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
        // compute() is atomic per-key: the lambda runs without concurrent
        // interference from other tryAcquireLock/releaseLock calls on the same
        // key. `acquired` is set from *inside* the lambda so it reflects
        // whether this specific invocation took the acquire branch — comparing
        // the returned map value against a locally computed `expiresAt` instead
        // would be unsafe, since two racing callers can compute an identical
        // `expiresAt` under coarse clock resolution and both appear to "win".
        boolean[] acquired = {false};
        locks.compute(storageKey, (key, existing) -> {
            if (existing != null && existing.longValue() > now) {
                acquired[0] = false;
                return existing; // lock is held — do not overwrite
            }
            acquired[0] = true;
            return expiresAt; // acquire or renew expired lock
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
