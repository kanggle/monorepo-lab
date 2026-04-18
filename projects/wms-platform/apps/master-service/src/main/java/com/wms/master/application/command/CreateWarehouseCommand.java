package com.wms.master.application.command;

public record CreateWarehouseCommand(
        String warehouseCode,
        String name,
        String address,
        String timezone,
        String actorId) {
}
