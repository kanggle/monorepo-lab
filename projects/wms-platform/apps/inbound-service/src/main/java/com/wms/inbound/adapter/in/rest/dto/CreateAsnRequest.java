package com.wms.inbound.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateAsnRequest(
        String asnNo,
        @NotNull UUID supplierPartnerId,
        @NotNull UUID warehouseId,
        LocalDate expectedArriveDate,
        String notes,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @NotNull UUID skuId,
            UUID lotId,
            @Positive int expectedQty
    ) {}
}
