package com.example.scmplatform.procurement.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record DraftPurchaseOrderRequest(
        @NotBlank @Size(max = 36) String supplierId,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @Positive int lineNo,
            @NotBlank @Size(max = 100) String sku,
            @Size(max = 100) String supplierSku,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0") BigDecimal unitPrice
    ) {
    }
}
