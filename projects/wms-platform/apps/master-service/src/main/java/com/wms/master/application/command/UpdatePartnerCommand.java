package com.wms.master.application.command;

import com.wms.master.domain.model.PartnerType;
import java.util.UUID;

/**
 * Update mutable fields of a Partner. Absent (null) fields are left unchanged.
 *
 * @param partnerCodeAttempt if non-null and different from the stored value,
 *                           rejected as an immutable-field attempt (surfaces
 *                           as {@code IMMUTABLE_FIELD})
 * @param version            caller-supplied optimistic-lock version; mismatch
 *                           against the stored version surfaces as {@code CONFLICT}
 */
public record UpdatePartnerCommand(
        UUID id,
        String name,
        PartnerType partnerType,
        String businessNumber,
        String contactName,
        String contactEmail,
        String contactPhone,
        String address,
        String partnerCodeAttempt,
        long version,
        String actorId) {
}
