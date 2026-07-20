package com.example.product.domain.repository;

import com.example.product.domain.model.Product;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Product save(Product product);

    /**
     * Persists {@code product} and flushes immediately, so a violation of a
     * DB-level constraint (e.g. {@code uq_product_variants_option UNIQUE
     * (product_id, option_name)}) surfaces as a synchronous
     * {@code DataIntegrityViolationException} at THIS call — inside the
     * caller's {@code try} — instead of being deferred to transaction commit,
     * where it would no longer be translatable into a domain exception and
     * would escape as a raw 500 (TASK-BE-536). {@link #save(Product)} stays
     * plain {@code save} for every other write path — this method exists only
     * for callers that must observe the constraint violation synchronously.
     */
    Product saveAndFlush(Product product);

    Optional<Product> findById(UUID id);

    boolean existsById(UUID id);

    void softDelete(UUID productId);

    /** Total non-deleted products for the current tenant. */
    long countAllForTenant();

    /** Non-deleted products created in [from, to) for the current tenant. */
    long countCreatedBetween(Instant from, Instant to);
}
