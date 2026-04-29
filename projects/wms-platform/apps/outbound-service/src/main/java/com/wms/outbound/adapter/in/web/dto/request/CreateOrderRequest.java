package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST request body for {@code POST /api/v1/outbound/orders}. Schema per
 * {@code specs/contracts/http/outbound-service-api.md} §1.1.
 */
public record CreateOrderRequest(
        @NotBlank @Size(min = 1, max = 40) String orderNo,
        @NotNull UUID customerPartnerId,
        @NotNull UUID warehouseId,
        LocalDate requiredShipDate,
        @Size(max = 1000) String notes,
        @NotEmpty @Valid List<CreateOrderLineRequest> lines
) {
}
