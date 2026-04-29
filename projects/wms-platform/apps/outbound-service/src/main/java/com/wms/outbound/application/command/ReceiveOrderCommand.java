package com.wms.outbound.application.command;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Input record for {@code ReceiveOrderUseCase}. Carries the manual REST or
 * webhook-driven order data plus auth context (actorId + caller roles).
 */
public record ReceiveOrderCommand(
        String orderNo,
        String source,
        UUID customerPartnerId,
        UUID warehouseId,
        LocalDate requiredShipDate,
        String notes,
        List<ReceiveOrderLineCommand> lines,
        String actorId,
        Set<String> callerRoles
) {
}
