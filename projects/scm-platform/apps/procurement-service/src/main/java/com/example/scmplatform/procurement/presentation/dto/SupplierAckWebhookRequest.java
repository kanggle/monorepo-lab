package com.example.scmplatform.procurement.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierAckWebhookRequest(
        @NotBlank @Size(max = 64) String tenantId,
        @NotBlank @Size(max = 36) String poId,
        @NotBlank @Size(max = 100) String supplierAckRef
) {
}
