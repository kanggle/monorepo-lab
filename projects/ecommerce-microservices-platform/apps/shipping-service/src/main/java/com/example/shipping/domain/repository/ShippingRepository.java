package com.example.shipping.domain.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShippingRepository {

    Shipping save(Shipping shipping);

    /**
     * Tenant-AGNOSTIC lookup by the globally-unique {@code shippingId}. Used by the
     * system carrier-webhook path ({@code ProcessCarrierWebhookService}) — the carrier
     * pushes a delivery event keyed by {@code shippingId} with no HTTP tenant context,
     * and the located row already carries its own tenant. Must NOT be tenant-filtered or
     * carrier webhooks would silently no-op other-tenant shipments (M3 system-path rule).
     */
    Optional<Shipping> findById(String shippingId);

    /**
     * Tenant-SCOPED lookup backing the admin/operator mutations
     * ({@code updateStatus} / {@code refreshFromCarrier}). A cross-tenant
     * {@code shippingId} resolves to empty so the caller raises 404 (existence hidden,
     * M3 cross-tenant-read-is-not-found). The tenant is the current request tenant
     * ({@code TenantContext.currentTenant()}), resolved inside the implementation.
     */
    Optional<Shipping> findByIdForTenant(String shippingId);

    /**
     * Tenant-AGNOSTIC lookup by the globally-unique {@code orderId}. Used by the
     * consumer "my shipping" read and the wms-confirm return leg
     * ({@code markShippedByOrderId}) — globally-unique key, system flows; the located
     * row keeps its own tenant. Must NOT be tenant-filtered (M3 system-path rule).
     */
    Optional<Shipping> findByOrderId(String orderId);

    /**
     * Tenant-AGNOSTIC existence check by the globally-unique {@code orderId}. Backs the
     * createShipping idempotency dedup — must see a pre-existing row in ANY tenant so a
     * re-delivered OrderConfirmed cannot create a duplicate (M3 system-path rule).
     */
    boolean existsByOrderId(String orderId);

    /** Tenant-SCOPED admin list (current request tenant) — operator list EP. */
    PageResult<Shipping> findAll(PageQuery pageQuery);

    /** Tenant-SCOPED admin list filtered by status (current request tenant). */
    PageResult<Shipping> findByStatus(ShippingStatus status, PageQuery pageQuery);

    /**
     * Tenant-SCOPED total count of all shipments for the current request tenant.
     * Resolves tenant via {@code TenantContext.currentTenant()} inside the impl.
     */
    long countAllForTenant();

    /**
     * Tenant-SCOPED count of shipments created within [{@code from}, {@code to}).
     * Resolves tenant via {@code TenantContext.currentTenant()} inside the impl.
     */
    long countCreatedBetween(Instant from, Instant to);

    /**
     * In-flight shipments eligible for the unattended auto-collect tracking sweep
     * (TASK-BE-360): status is {@code SHIPPED} or {@code IN_TRANSIT} (en route — neither
     * not-yet-shipped {@code PREPARING} nor terminal {@code DELIVERED}) AND both a
     * tracking number and a carrier are present (so the carrier port can be queried).
     * Ordered oldest-updated first and capped at {@code limit} so a single tick processes a
     * bounded batch; overflow carries to the next tick (no unbounded loop — ADR-007 / spec
     * Edge Cases).
     *
     * <p>Tenant-AGNOSTIC: the unattended sweep runs on a background thread with no HTTP
     * tenant context and must process EVERY tenant's in-flight shipments (each advanced
     * row keeps its own tenant). Tenant-filtering this would strand other-tenant
     * shipments at SHIPPED/IN_TRANSIT forever (M3 system-path rule).
     */
    List<Shipping> findInFlightWithTracking(int limit);
}
