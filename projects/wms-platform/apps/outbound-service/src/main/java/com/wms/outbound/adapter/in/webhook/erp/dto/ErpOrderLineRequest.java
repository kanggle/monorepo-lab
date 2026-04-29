package com.wms.outbound.adapter.in.webhook.erp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Per-line entry on the ERP order webhook payload. Schema declared in
 * {@code specs/contracts/webhooks/erp-order-webhook.md} § Request Body.
 */
public record ErpOrderLineRequest(
        @NotNull
        @Min(1)
        Integer lineNo,

        @NotBlank
        @Size(min = 1, max = 40)
        String skuCode,

        @Size(max = 40)
        String lotNo,

        @NotNull
        @Positive
        Integer qtyOrdered
) {
}
