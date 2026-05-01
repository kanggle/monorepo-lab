package com.wms.outbound.application.command;

import java.util.Set;
import java.util.UUID;

/**
 * Input record for {@code ConfirmShippingUseCase}. Mirrors
 * {@code outbound-service-api.md} §4.1 request body.
 */
public record ConfirmShippingCommand(
        UUID orderId,
        long expectedVersion,
        String carrierCode,
        String actorId,
        Set<String> callerRoles
) {
}
