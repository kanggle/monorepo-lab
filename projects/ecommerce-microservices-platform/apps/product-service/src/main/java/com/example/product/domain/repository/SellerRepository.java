package com.example.product.domain.repository;

import com.example.product.domain.model.Seller;

import java.util.Optional;

/**
 * Persistence port for the marketplace {@link Seller} aggregate (ADR-MONO-030
 * Step 3 §3.1). All operations are implicitly scoped to the current tenant
 * (the adapter stamps/filters {@code tenant_id} via {@code TenantContext}), so a
 * seller is always addressed within its tenant — the composite key
 * {@code (tenant_id, seller_id)} never crosses the tenant boundary (AC-6).
 */
public interface SellerRepository {

    /** Persists a new seller under the current tenant. */
    Seller save(Seller seller);

    /** Looks up a seller by id within the current tenant. */
    Optional<Seller> findById(String sellerId);

    /** Whether a seller with this id exists within the current tenant. */
    boolean existsById(String sellerId);

    /**
     * Ensures the per-tenant default seller exists, creating it if absent
     * (idempotent). Returns the (existing or newly-created) default seller —
     * the standalone / single-seller degradation anchor (D8, AC-5).
     */
    Seller ensureDefaultSeller();
}
