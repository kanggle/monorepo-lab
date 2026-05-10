package com.wms.outbound.application.command;

import java.util.Set;
import java.util.UUID;

/**
 * Command for {@code POST /api/v1/outbound/shipments/{id}:retry-tms-notify}.
 *
 * @param shipmentId  shipment to retry
 * @param actorId     authenticated user / service id (for audit)
 * @param callerRoles role set extracted from the JWT; must contain
 *                    {@code ROLE_OUTBOUND_ADMIN}
 */
public record RetryTmsNotificationCommand(
        UUID shipmentId,
        String actorId,
        Set<String> callerRoles) {
}
