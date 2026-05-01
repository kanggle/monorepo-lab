package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * REST request body for {@code POST /api/v1/outbound/orders/{id}:cancel}.
 */
public record CancelOrderRequest(
        @NotBlank @Size(min = 3, max = 500) String reason,
        @Min(0) long version
) {
}
