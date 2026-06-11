package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;

import java.time.Instant;

public record PolicyResponse(
        String skuCode,
        int reorderPoint,
        int safetyStock,
        int reorderQty,
        int version,
        Instant updatedAt
) {
    public static PolicyResponse from(ReorderPolicy p) {
        return new PolicyResponse(p.getSkuCode(), p.getReorderPoint(), p.getSafetyStock(),
                p.getReorderQty(), p.getVersion(), p.getUpdatedAt());
    }
}
