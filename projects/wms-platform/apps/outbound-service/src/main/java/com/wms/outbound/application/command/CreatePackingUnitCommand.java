package com.wms.outbound.application.command;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Input record for {@code CreatePackingUnitUseCase}. Mirrors
 * {@code outbound-service-api.md} §3.1 request body.
 */
public record CreatePackingUnitCommand(
        UUID orderId,
        String cartonNo,
        String packingType,
        Integer weightGrams,
        Integer lengthMm,
        Integer widthMm,
        Integer heightMm,
        String notes,
        List<CreatePackingUnitLineCommand> lines,
        String actorId,
        Set<String> callerRoles
) {
}
