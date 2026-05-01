package com.wms.outbound.application.command;

import java.util.Set;
import java.util.UUID;

/**
 * Input record for {@code CancelOrderUseCase}.
 *
 * <p>Authorization is enforced in the application layer: roles
 * {@code ROLE_OUTBOUND_WRITE} suffice for pre-pick cancellation
 * (order in {@code RECEIVED}/{@code PICKING}); post-pick
 * cancellation requires {@code ROLE_OUTBOUND_ADMIN}.
 */
public record CancelOrderCommand(
        UUID orderId,
        String reason,
        long expectedVersion,
        String actorId,
        Set<String> callerRoles
) {
}
