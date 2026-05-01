package com.wms.inbound.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkipPutawayLineRequest(
        @NotBlank @Size(min = 3, max = 500) String reason
) {}
