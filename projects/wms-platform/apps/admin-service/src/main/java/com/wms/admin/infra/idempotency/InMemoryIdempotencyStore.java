package com.wms.admin.infra.idempotency;

import com.wms.admin.application.port.IdempotencyStore;
import com.wms.admin.application.port.StoredResponse;
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
        Long existing = locks.get(storageKey);
        if (existing != null && existing > now) {
            return false;
        }
        locks.put(storageKey, expiresAt);
        return true;
    }

    @Override
    public void releaseLock(String storageKey) {
        locks.remove(storageKey);
    }

    private record Entry(StoredResponse response, Instant expiresAt) {
    }
}
