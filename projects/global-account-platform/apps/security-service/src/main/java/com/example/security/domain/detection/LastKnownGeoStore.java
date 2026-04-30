package com.example.security.domain.detection;

import java.time.Instant;
import java.util.Optional;

/**
 * Port for per-account last-known geo cache (Redis hash, TTL 30 days).
 */
public interface LastKnownGeoStore {

    Optional<Snapshot> get(String accountId);

    void put(String accountId, Snapshot snapshot);

    record Snapshot(String country, double latitude, double longitude, Instant occurredAt) {}
}
