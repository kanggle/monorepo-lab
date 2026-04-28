package com.wms.inbound.adapter.in.webhook.erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Wire format of an ERP ASN push webhook body.
 *
 * <p>Schema authority: {@code specs/contracts/webhooks/erp-asn-webhook.md} §
 * Request Body. v1 ignores unknown fields for forward-compatibility.
 */
public record ErpAsnWebhookRequest(
        @NotBlank
        @Size(min = 1, max = 40)
        String asnNo,

        @NotBlank
        @Size(min = 1, max = 40)
        String supplierPartnerCode,

        @NotBlank
        @Size(min = 1, max = 20)
        String warehouseCode,

        LocalDate expectedArriveDate,

        @Size(max = 1000)
        String notes,

        @NotEmpty
        @Valid
        List<ErpAsnLineRequest> lines
) {
}
