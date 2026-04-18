package com.wms.master.application.command;

import java.util.UUID;

/**
 * Update mutable fields of a warehouse. Absent (null) fields are left unchanged.
 *
 * @param version caller-supplied optimistic-lock version; mismatch against the
 *                stored version surfaces as {@code CONFLICT}
 */
public record UpdateWarehouseCommand(
        UUID id,
        String name,
        String address,
        String timezone,
        long version,
        String actorId) {
}
