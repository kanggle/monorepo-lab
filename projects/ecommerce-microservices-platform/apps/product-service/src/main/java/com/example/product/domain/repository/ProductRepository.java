package com.example.product.domain.repository;

import com.example.product.domain.model.Product;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    boolean existsById(UUID id);

    void softDelete(UUID productId);

    /** Total non-deleted products for the current tenant. */
    long countByTenant();

    /** Non-deleted products created in [from, to) for the current tenant. */
    long countByTenantCreatedBetween(Instant from, Instant to);
}
