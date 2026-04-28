package com.wms.inventory.application.command;

import java.util.List;
import java.util.UUID;

/**
 * Application-level translation of an {@code inbound.putaway.completed} event.
 * Carries the source event's id (for {@code source_event_id} on every Movement
 * row) and {@code asnId} (for the published {@code inventory.received} event).
 */
public record ReceiveStockCommand(
        UUID sourceEventId,
        UUID warehouseId,
        UUID asnId,
        List<ReceiveStockLineCommand> lines,
        String actorId
) {
    public ReceiveStockCommand {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        lines = List.copyOf(lines);
    }
}
