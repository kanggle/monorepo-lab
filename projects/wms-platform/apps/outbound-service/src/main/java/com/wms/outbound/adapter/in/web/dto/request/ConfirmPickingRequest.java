package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * REST request body for {@code POST /api/v1/outbound/picking-requests/{id}/confirmations}.
 * Schema per {@code outbound-service-api.md} §2.3.
 */
public record ConfirmPickingRequest(
        @Size(max = 500) String notes,
        @NotEmpty @Valid List<ConfirmPickingLineRequest> lines
) {
}
