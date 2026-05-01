package com.wms.outbound.application.command;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Input record for {@code ConfirmPickingUseCase}. Mirrors
 * {@code outbound-service-api.md} §2.3 request body.
 */
public record ConfirmPickingCommand(
        UUID orderId,
        String notes,
        List<ConfirmPickingLineCommand> lines,
        String actorId,
        Set<String> callerRoles
) {
}
