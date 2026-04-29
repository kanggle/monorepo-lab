package com.wms.outbound.application.command;

import java.util.UUID;

/**
 * Per-line entry on {@link ReceiveOrderCommand}.
 */
public record ReceiveOrderLineCommand(
        int lineNo,
        UUID skuId,
        UUID lotId,
        int qtyOrdered
) {
}
