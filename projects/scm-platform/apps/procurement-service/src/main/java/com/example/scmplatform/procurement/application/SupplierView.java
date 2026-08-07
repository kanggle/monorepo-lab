package com.example.scmplatform.procurement.application;

import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;

import java.time.Instant;

/**
 * Read-model projection of a {@link Supplier}. Mirrors
 * {@link PurchaseOrderView}'s role: keeps presentation DTOs off the JPA entity
 * so controller slice tests can build views without Hibernate.
 *
 * <p>Carries no credential field, and must not grow one — v1 accepts no
 * supplier credentials on any path (ADR-SCM-001 ACCEPT rider).
 */
public record SupplierView(
        String id,
        String tenantId,
        String code,
        String name,
        SupplierStatus status,
        Instant contractStartedAt,
        Instant contractExpiresAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static SupplierView from(Supplier s) {
        return new SupplierView(
                s.getId(),
                s.getTenantId(),
                s.getCode(),
                s.getName(),
                s.getStatus(),
                s.getContractStartedAt(),
                s.getContractExpiresAt(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
