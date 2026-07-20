package com.example.product.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A product-registration request that was accepted for one tenant under one client
 * {@code Idempotency-Key} (TASK-BE-536, Flyway V18 {@code product_create_request}).
 *
 * <p>Exists so {@code POST /api/admin/products} can tell "a retry of the
 * registration I already performed" apart from "a second, intentionally
 * same-named product" — a name-uniqueness natural key cannot separate the two
 * (two genuinely different products can share a name), so only a client key can.
 *
 * <p>{@code name} is recorded because the key is bound to it: a replay of the same
 * key with a <em>different</em> name is rejected (409) rather than silently
 * returning the first product.
 *
 * <p>Immutable — a request record is never mutated after acceptance. Kept
 * indefinitely (no TTL), mirroring {@code RefundRequest} (TASK-BE-535).
 */
public final class ProductCreateRequest {

    private final Long id;
    private final String tenantId;
    private final String idempotencyKey;
    private final String name;
    private final UUID productId;
    private final Instant createdAt;

    private ProductCreateRequest(Long id, String tenantId, String idempotencyKey, String name,
                                 UUID productId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.name = name;
        this.productId = productId;
        this.createdAt = createdAt;
    }

    /**
     * A not-yet-persisted record for an incoming registration request.
     * {@code productId} is the id already assigned to the (not-yet-persisted)
     * {@link Product} aggregate — generated client-side by {@code Product.create},
     * so it is known before the claim row is inserted.
     */
    public static ProductCreateRequest of(String tenantId, String idempotencyKey, String name,
                                          UUID productId, Instant createdAt) {
        return new ProductCreateRequest(null, tenantId, idempotencyKey, name, productId, createdAt);
    }

    /** Rehydrates a persisted record. */
    public static ProductCreateRequest reconstitute(Long id, String tenantId, String idempotencyKey,
                                                     String name, UUID productId, Instant createdAt) {
        return new ProductCreateRequest(id, tenantId, idempotencyKey, name, productId, createdAt);
    }

    /** True iff this recorded request was for exactly {@code candidateName}. */
    public boolean matchesName(String candidateName) {
        return this.name.equals(candidateName);
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getName() {
        return name;
    }

    public UUID getProductId() {
        return productId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
