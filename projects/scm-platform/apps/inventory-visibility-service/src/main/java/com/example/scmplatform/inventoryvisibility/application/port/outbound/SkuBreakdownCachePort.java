package com.example.scmplatform.inventoryvisibility.application.port.outbound;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Optional;

/**
 * Outbound port for the SKU aggregation read-model cache.
 *
 * <p>Implementations are free to use any backing store (Redis, in-process, etc.).
 * The port contract enforces **fail-open** semantics: a cache miss or a backing-store
 * outage MUST result in {@link Optional#empty()} (never throw), and
 * {@link #isAvailable()} returns {@code false} when the backing store is offline.
 *
 * <p>The {@code X-Cache: HIT | MISS | UNAVAILABLE} response header is set by the
 * controller after interpreting the {@link Optional} + {@link #isAvailable()} result —
 * the port itself carries no HTTP concern.
 *
 * <p>Registered as port in TASK-SCM-BE-017 A1 to remove the
 * {@code adapter.outbound.cache.*} direct import from
 * {@code InventoryVisibilityController}.
 */
public interface SkuBreakdownCachePort {

    /**
     * Retrieve a cached value for the given SKU + tenant combination.
     *
     * @param sku      the SKU identifier
     * @param tenantId the tenant scope
     * @param typeRef  Jackson {@link TypeReference} describing the target type;
     *                 callers pass a concrete anonymous subclass so that generic
     *                 type information is preserved at runtime
     * @param <T>      the expected cached type
     * @return {@link Optional#empty()} on cache miss or backing-store failure
     *         (fail-open); a present value on cache hit
     */
    <T> Optional<T> get(String sku, String tenantId, TypeReference<T> typeRef);

    /**
     * Store a value in the cache for the given SKU + tenant combination.
     *
     * <p>Failures MUST be swallowed (logged only) — the cache is a best-effort
     * acceleration layer, never a required write path.
     *
     * @param sku      the SKU identifier
     * @param tenantId the tenant scope
     * @param value    the value to cache; must be JSON-serialisable
     * @param <T>      the type of the cached value
     */
    <T> void put(String sku, String tenantId, T value);

    /**
     * Returns {@code true} if the backing store is currently reachable.
     *
     * <p>Callers use this to decide whether to attempt a {@link #put} and to
     * populate the {@code X-Cache: UNAVAILABLE} header when the store is offline.
     */
    boolean isAvailable();
}
