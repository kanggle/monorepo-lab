package com.wms.outbound.application.command;

import java.util.Set;
import java.util.UUID;

/**
 * Input record for {@code SealPackingUnitUseCase}.
 */
public record SealPackingUnitCommand(
        UUID orderId,
        UUID packingUnitId,
        long expectedVersion,
        String actorId,
        Set<String> callerRoles
) {
}
