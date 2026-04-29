package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ConfirmShippingRequest(
        @Size(max = 40) String carrierCode,
        @Min(0) long version
) {
}
