package com.wms.admin.api.assignment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GrantAssignmentRequest(
        @NotNull UUID userId,
        @NotNull UUID roleId,
        UUID warehouseId) {
}
