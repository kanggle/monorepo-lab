package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.DeactivateSkuCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DeactivateSkuRequest(
        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version,

        @Size(max = 500, message = "reason must be <= 500 characters")
        String reason) {

    public DeactivateSkuCommand toCommand(UUID id, String actorId) {
        return new DeactivateSkuCommand(id, reason, version, actorId);
    }
}
