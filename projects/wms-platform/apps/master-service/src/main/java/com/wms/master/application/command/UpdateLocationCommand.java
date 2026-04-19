package com.wms.master.application.command;

import com.wms.master.domain.model.LocationType;
import java.util.UUID;

/**
 * Update mutable fields of a location. Absent (null) fields are left unchanged.
 *
 * @param locationCodeAttempt if non-null, rejected as an immutable-field attempt
 *                            (surfaces as {@code IMMUTABLE_FIELD})
 * @param warehouseIdAttempt  if non-null, rejected the same way
 * @param zoneIdAttempt       if non-null, rejected the same way
 * @param version             caller-supplied optimistic-lock version
 */
public record UpdateLocationCommand(
        UUID id,
        LocationType locationType,
        Integer capacityUnits,
        String aisle,
        String rack,
        String level,
        String bin,
        String locationCodeAttempt,
        UUID warehouseIdAttempt,
        UUID zoneIdAttempt,
        long version,
        String actorId) {
}
