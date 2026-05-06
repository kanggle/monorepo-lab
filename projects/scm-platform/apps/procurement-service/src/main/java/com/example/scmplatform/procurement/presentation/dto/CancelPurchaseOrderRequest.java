package com.example.scmplatform.procurement.presentation.dto;

import jakarta.validation.constraints.Size;

public record CancelPurchaseOrderRequest(
        @Size(max = 200) String reason
) {
}
