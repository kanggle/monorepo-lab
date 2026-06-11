package com.example.scmplatform.demandplanning.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * scm-owned reorder decision rule for a SKU (ADR-027 D4).
 * Distinct from the wms alert threshold — the scm reorder point is the
 * authoritative comparison for raising a reorder suggestion.
 */
public class ReorderPolicy {

    private final String skuCode;
    private final int reorderPoint;
    private final int safetyStock;
    private final int reorderQty;
    private final String tenantId;
    private int version;
    private Instant updatedAt;

    public ReorderPolicy(String skuCode, int reorderPoint, int safetyStock,
                         int reorderQty, String tenantId, int version, Instant updatedAt) {
        Objects.requireNonNull(skuCode, "skuCode");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (reorderPoint < 0) throw new IllegalArgumentException("reorderPoint must be >= 0");
        if (reorderQty <= 0) throw new IllegalArgumentException("reorderQty must be > 0");
        if (safetyStock < 0) throw new IllegalArgumentException("safetyStock must be >= 0");
        this.skuCode = skuCode;
        this.reorderPoint = reorderPoint;
        this.safetyStock = safetyStock;
        this.reorderQty = reorderQty;
        this.tenantId = tenantId;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    /**
     * Evaluate whether a reorder should be raised based on available quantity.
     * Rule: availableQty <= reorderPoint → raise.
     */
    public boolean shouldReorder(int availableQty) {
        return availableQty <= reorderPoint;
    }

    public String getSkuCode() { return skuCode; }
    public int getReorderPoint() { return reorderPoint; }
    public int getSafetyStock() { return safetyStock; }
    public int getReorderQty() { return reorderQty; }
    public String getTenantId() { return tenantId; }
    public int getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
