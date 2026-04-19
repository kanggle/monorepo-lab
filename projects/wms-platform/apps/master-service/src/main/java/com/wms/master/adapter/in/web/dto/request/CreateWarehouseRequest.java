package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.CreateWarehouseCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotBlank(message = "warehouseCode is required")
        @Pattern(regexp = "^WH\\d{2,3}$", message = "warehouseCode must match ^WH\\d{2,3}$")
        String warehouseCode,

        @NotBlank(message = "name is required")
        @Size(min = 1, max = 100, message = "name must be 1..100 characters")
        String name,

        @Size(max = 200, message = "address must be <= 200 characters")
        String address,

        @NotBlank(message = "timezone is required")
        String timezone) {

    public CreateWarehouseCommand toCommand(String actorId) {
        return new CreateWarehouseCommand(warehouseCode, name, address, timezone, actorId);
    }
}
