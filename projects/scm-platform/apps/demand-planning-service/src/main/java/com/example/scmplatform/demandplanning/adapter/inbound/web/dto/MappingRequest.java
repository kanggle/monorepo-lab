package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SKU→supplier mapping upsert request.
 *
 * <p>ADR-MONO-050 D9 (Option A): {@code supplierId} is a supplier <em>business code</em>
 * (String, e.g. {@code "SUP-0043"}), not a UUID — it flows to the PO {@code supplierId}
 * and wms resolves it via {@code findPartnerByCode}. Capped at 36 to match the
 * procurement {@code purchase_orders.supplier_id} column.
 */
public record MappingRequest(
        @NotBlank @Size(max = 36) String supplierId,
        @NotNull @Min(1) Integer defaultOrderQty,
        @NotNull @Min(0) Integer leadTimeDays,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
