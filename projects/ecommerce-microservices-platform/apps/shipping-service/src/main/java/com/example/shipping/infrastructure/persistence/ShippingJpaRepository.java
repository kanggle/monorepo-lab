package com.example.shipping.infrastructure.persistence;

import com.example.shipping.domain.model.ShippingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShippingJpaRepository extends JpaRepository<ShippingJpaEntity, String> {

    Optional<ShippingJpaEntity> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    /**
     * Tenant-scoped single-row lookup backing the admin/operator mutations
     * (updateStatus / refreshFromCarrier — M3). A cross-tenant {@code shippingId}
     * resolves to empty so the caller 404s (existence hidden, M3 cross-tenant-read-is-
     * not-found). NOT used by the system/consumer/webhook paths, which use the
     * tenant-agnostic {@link #findById(Object)} on a globally-unique key.
     */
    Optional<ShippingJpaEntity> findByShippingIdAndTenantId(String shippingId, String tenantId);

    /** Tenant-scoped admin list (no status filter) — backs {@code findAll}. */
    Page<ShippingJpaEntity> findByTenantId(String tenantId, Pageable pageable);

    /** Tenant-scoped admin list filtered by status — backs {@code findByStatus}. */
    Page<ShippingJpaEntity> findByTenantIdAndStatus(String tenantId, ShippingStatus status, Pageable pageable);

    /** Tenant-scoped total count — backs {@code countAllForTenant}. */
    long countByTenantId(String tenantId);

    /** Tenant-scoped count within a createdAt window — backs period-count queries. */
    long countByTenantIdAndCreatedAtBetween(String tenantId, Instant from, Instant to);

    Page<ShippingJpaEntity> findByStatus(ShippingStatus status, Pageable pageable);

    /**
     * In-flight shipments (status ∈ {@code statuses}) that carry a non-blank tracking number
     * AND carrier, oldest-updated first. Paged by the caller to a bounded batch (TASK-BE-360).
     */
    @Query("""
            SELECT s FROM ShippingJpaEntity s
            WHERE s.status IN :statuses
              AND s.trackingNumber IS NOT NULL AND s.trackingNumber <> ''
              AND s.carrier IS NOT NULL AND s.carrier <> ''
            ORDER BY s.updatedAt ASC
            """)
    List<ShippingJpaEntity> findInFlightWithTracking(@Param("statuses") Collection<ShippingStatus> statuses,
                                                     Pageable pageable);
}
