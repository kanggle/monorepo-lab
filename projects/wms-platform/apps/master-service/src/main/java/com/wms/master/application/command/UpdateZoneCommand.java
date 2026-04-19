package com.wms.master.application.command;

import com.wms.master.domain.model.ZoneType;
import java.util.UUID;

/**
 * Update mutable fields of a zone. Absent (null) fields are left unchanged.
 *
 * @param zoneCodeAttempt    if non-null, rejected as an immutable-field attempt
 *                           (surfaces as {@code IMMUTABLE_FIELD})
 * @param warehouseIdAttempt if non-null, rejected the same way
 * @param version            caller-supplied optimistic-lock version; mismatch
 *                           against the stored version surfaces as {@code CONFLICT}
 */
public record UpdateZoneCommand(
        UUID id,
        String name,
        ZoneType zoneType,
        String zoneCodeAttempt,
        UUID warehouseIdAttempt,
        long version,
        String actorId) {
}
