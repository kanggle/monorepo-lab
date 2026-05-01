package com.wms.outbound.application.command;

import java.util.Set;
import java.util.UUID;

/**
 * Input record for {@code ConfirmPackingUseCase}. Triggered when the operator
 * declares all packing units are sealed and the order is ready to advance to
 * {@code PACKED}.
 */
public record ConfirmPackingCommand(
        UUID orderId,
        long expectedVersion,
        String actorId,
        Set<String> callerRoles
) {
}
