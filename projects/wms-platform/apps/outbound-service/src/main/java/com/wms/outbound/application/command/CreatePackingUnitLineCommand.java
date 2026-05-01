package com.wms.outbound.application.command;

import java.util.UUID;

/**
 * One line of a {@link CreatePackingUnitCommand}. Mirrors
 * {@code outbound-service-api.md} §3.1 request body line shape.
 */
public record CreatePackingUnitLineCommand(
        UUID orderLineId,
        UUID skuId,
        UUID lotId,
        int qty
) {
}
