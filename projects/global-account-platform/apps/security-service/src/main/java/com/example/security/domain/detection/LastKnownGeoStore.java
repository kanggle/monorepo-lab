package com.example.security.domain.detection;

import java.time.Instant;
import java.util.Optional;

/**
 * Port for per-tenant per-account last-known geo cache (Redis hash, TTL 30 days).
 *
 * <p>TASK-BE-248 Phase 1: all operations require {@code tenantId} so that geo
 * snapshots are isolated per tenant. The Redis key format is
 * {@code security:geo:last:{tenantId}:{accountId}}.
 */
public interface LastKnownGeoStore {

    /**
     * Returns the last-known geo snapshot for the given tenant/account pair.
     *
     * @param tenantId  tenant identifier (must be non-null/non-blank)
     * @param accountId account identifier
     */
    Optional<Snapshot> get(String tenantId, String accountId);

    /**
     * Stores a geo snapshot for the given tenant/account pair.
     *
     * @param tenantId  tenant identifier (must be non-null/non-blank)
     * @param accountId account identifier
     * @param snapshot  geo data to persist
     */
    void put(String tenantId, String accountId, Snapshot snapshot);

    record Snapshot(String country, double latitude, double longitude, Instant occurredAt) {}
}
