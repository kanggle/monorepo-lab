package com.example.scmplatform.demandplanning.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Minimal SKU→supplier mapping (ADR-027 D3).
 * Stand-in for the v2-deferred supplier-service.
 */
public class SkuSupplierMapping {

    private final String skuCode;
    private final UUID supplierId;
    private final int defaultOrderQty;
    private final int leadTimeDays;
    private final String currency;
    private final String tenantId;

    public SkuSupplierMapping(String skuCode, UUID supplierId, int defaultOrderQty,
                               int leadTimeDays, String currency, String tenantId) {
        Objects.requireNonNull(skuCode, "skuCode");
        Objects.requireNonNull(supplierId, "supplierId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(tenantId, "tenantId");
        if (defaultOrderQty <= 0) throw new IllegalArgumentException("defaultOrderQty must be > 0");
        if (leadTimeDays < 0) throw new IllegalArgumentException("leadTimeDays must be >= 0");
        this.skuCode = skuCode;
        this.supplierId = supplierId;
        this.defaultOrderQty = defaultOrderQty;
        this.leadTimeDays = leadTimeDays;
        this.currency = currency;
        this.tenantId = tenantId;
    }

    public String getSkuCode() { return skuCode; }
    public UUID getSupplierId() { return supplierId; }
    public int getDefaultOrderQty() { return defaultOrderQty; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public String getCurrency() { return currency; }
    public String getTenantId() { return tenantId; }
}
