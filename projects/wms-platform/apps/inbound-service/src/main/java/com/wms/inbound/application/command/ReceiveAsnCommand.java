package com.wms.inbound.application.command;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ReceiveAsnCommand(
        String asnNo,
        String source,
        UUID supplierPartnerId,
        UUID warehouseId,
        LocalDate expectedArriveDate,
        String notes,
        List<Line> lines,
        String actorId,
        Set<String> callerRoles
) {
    public record Line(
            UUID skuId,
            UUID lotId,
            int expectedQty
    ) {}
}
