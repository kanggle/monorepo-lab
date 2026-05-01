package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;

public record ConfirmPackingRequest(
        @Min(0) long version
) {
}
