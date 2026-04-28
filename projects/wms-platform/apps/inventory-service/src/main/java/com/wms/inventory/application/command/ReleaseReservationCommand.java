package com.wms.inventory.application.command;

import com.wms.inventory.domain.model.ReleasedReason;
import java.util.UUID;

public record ReleaseReservationCommand(
        UUID reservationId,
        ReleasedReason reason,
        Long expectedVersion,
        UUID sourceEventId,
        String actorId
) {
}
