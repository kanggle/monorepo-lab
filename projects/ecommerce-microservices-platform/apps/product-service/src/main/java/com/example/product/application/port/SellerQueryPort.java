package com.example.product.application.port;

import com.example.product.application.dto.SellerListResult;

/**
 * Application read port for the marketplace seller operator surface (ADR-MONO-030
 * Step 4 facet f). Mirrors {@link ProductQueryPort}: the summary-list read lives on
 * an application port (not the domain {@code SellerRepository}), so the domain layer
 * stays free of presentation/list DTOs. The adapter scopes every read to the current
 * tenant via {@code TenantContext} (M6 — no cross-tenant leak).
 */
public interface SellerQueryPort {

    /** Paged list of sellers in the current tenant, newest-first. */
    SellerListResult findAll(int page, int size);
}
