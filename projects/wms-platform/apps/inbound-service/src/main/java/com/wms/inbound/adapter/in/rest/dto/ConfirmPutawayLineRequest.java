package com.wms.inbound.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ConfirmPutawayLineRequest(
        @NotNull UUID actualLocationId,
        @Positive int qtyConfirmed
) {}
