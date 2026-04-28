package com.wms.inventory.application.command;

import java.util.UUID;

/**
 * One line of an {@code inbound.putaway.completed} event after parsing.
 * The use-case processes all lines of one event in one transaction.
 */
public record ReceiveStockLineCommand(
        UUID locationId,
        UUID skuId,
        UUID lotId,
        int qtyReceived
) {
    public ReceiveStockLineCommand {
        if (qtyReceived <= 0) {
            throw new IllegalArgumentException("qtyReceived must be > 0, got: " + qtyReceived);
        }
    }
}
