package com.example.scmplatform.procurement.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AsnWebhookRequest(
        @NotBlank @Size(max = 64) String tenantId,
        @NotBlank @Size(max = 36) String poId,
        @NotBlank @Size(max = 100) String supplierAsnRef,
        @NotNull Instant expectedArrivalAt,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @NotBlank @Size(max = 36) String poLineId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantityShipped
    ) {
    }
}
