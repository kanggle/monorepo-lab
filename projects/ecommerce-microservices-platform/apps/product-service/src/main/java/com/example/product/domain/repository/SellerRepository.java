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

    /**
     * Applies a mutated seller's lifecycle fields (status / account_id / identity_id /
     * display_name) onto the existing row within the current tenant (ADR-MONO-042 —
     * provisioning success + suspend/close transitions update an existing seller).
     * Returns the persisted aggregate.
     */
    Seller update(Seller seller);

    /** Looks up a seller by id within the current tenant. */
    Optional<Seller> findById(String sellerId);

    /**
     * Looks up a seller by its backing IAM {@code account_id} within the current tenant
     * (ADR-MONO-042 D4-C, TASK-BE-421 — reverse {@code account.status.changed} projection).
     * Scoped to the current tenant: the same account never backs sellers across tenants.
     */
    Optional<Seller> findByAccountId(String accountId);

    /** Whether a seller with this id exists within the current tenant. */
    boolean existsById(String sellerId);

    /**
     * Ensures the per-tenant default seller exists, creating it if absent
     * (idempotent). Returns the (existing or newly-created) default seller —
     * the standalone / single-seller degradation anchor (D8, AC-5).
     */
    Seller ensureDefaultSeller();
}
