package com.example.scmplatform.demandplanning.domain.model;

import java.util.Objects;

/**
 * Minimal SKU→supplier mapping (ADR-027 D3).
 * Stand-in for the v2-deferred supplier-service.
 *
 * <p><strong>ADR-MONO-050 D9 (Option A).</strong> {@code supplierId} is a supplier
 * <em>business code</em> (String), not a UUID — it flows to the PO {@code supplierId}
 * and wms resolves it via {@code findPartnerByCode}. Treating
 * {@code sku_supplier_map.supplier_id} as the supplier CODE is the v1 contract
 * (seeds/fixtures use codes such as {@code "SUP-0043"}).
 */
public class SkuSupplierMapping {

    private final String skuCode;
    private final String supplierId;
    private final int defaultOrderQty;
    private final int leadTimeDays;
    private final String currency;
    private final String tenantId;

    public SkuSupplierMapping(String skuCode, String supplierId, int defaultOrderQty,
                               int leadTimeDays, String currency, String tenantId) {
        Objects.requireNonNull(skuCode, "skuCode");
        Objects.requireNonNull(supplierId, "supplierId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(tenantId, "tenantId");
        if (supplierId.isBlank()) throw new IllegalArgumentException("supplierId must not be blank");
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
    public String getSupplierId() { return supplierId; }
    public int getDefaultOrderQty() { return defaultOrderQty; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public String getCurrency() { return currency; }
    public String getTenantId() { return tenantId; }
}
