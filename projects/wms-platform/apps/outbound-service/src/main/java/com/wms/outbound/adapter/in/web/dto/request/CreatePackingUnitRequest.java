package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePackingUnitRequest(
        @NotBlank @Size(min = 1, max = 40) String cartonNo,
        @NotBlank String packingType,
        @PositiveOrZero Integer weightGrams,
        @PositiveOrZero Integer lengthMm,
        @PositiveOrZero Integer widthMm,
        @PositiveOrZero Integer heightMm,
        @Size(max = 500) String notes,
        @NotEmpty @Valid List<CreatePackingUnitLineRequest> lines
) {
}
