package com.wms.outbound.adapter.in.webhook.erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Wire format of an ERP order push webhook body.
 *
 * <p>Schema authority: {@code specs/contracts/webhooks/erp-order-webhook.md} §
 * Request Body. v1 ignores unknown fields for forward-compatibility.
 */
public record ErpOrderWebhookRequest(
        @NotBlank
        @Size(min = 1, max = 40)
        String orderNo,

        @NotBlank
        @Size(min = 1, max = 40)
        String customerPartnerCode,

        @NotBlank
        @Size(min = 1, max = 20)
        String warehouseCode,

        LocalDate requiredShipDate,

        @Size(max = 1000)
        String notes,

        @NotEmpty
        @Valid
        List<ErpOrderLineRequest> lines
) {
}
