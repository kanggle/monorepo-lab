package com.example.scmplatform.procurement.presentation.dto;

import com.example.scmplatform.procurement.application.SupplierView;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;

import java.time.Instant;

public record SupplierResponse(
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

    public static SupplierResponse from(SupplierView v) {
        return new SupplierResponse(
                v.id(),
                v.tenantId(),
                v.code(),
                v.name(),
                v.status(),
                v.contractStartedAt(),
                v.contractExpiresAt(),
                v.createdAt(),
                v.updatedAt()
        );
    }
}
