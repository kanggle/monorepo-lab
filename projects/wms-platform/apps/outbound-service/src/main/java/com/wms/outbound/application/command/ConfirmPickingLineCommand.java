package com.wms.outbound.application.command;

import java.util.UUID;

/**
 * One line of a {@link ConfirmPickingCommand}. Mirrors
 * {@code outbound-service-api.md} §2.3 request body line shape.
 */
public record ConfirmPickingLineCommand(
        UUID orderLineId,
        UUID skuId,
        UUID lotId,
        UUID actualLocationId,
        int qtyConfirmed
) {
}
