package com.wms.inventory.application.command;

import java.util.List;
import java.util.UUID;

public record ConfirmReservationCommand(
        UUID reservationId,
        long expectedVersion,
        List<Line> lines,
        UUID sourceEventId,
        String actorId
) {

    public ConfirmReservationCommand {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        lines = List.copyOf(lines);
    }

    public record Line(UUID reservationLineId, int shippedQuantity) {
        public Line {
            if (shippedQuantity <= 0) {
                throw new IllegalArgumentException("shippedQuantity must be > 0");
            }
        }
    }
}
