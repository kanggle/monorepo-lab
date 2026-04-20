package com.wms.master.domain.exception;

import java.util.UUID;

/**
 * Thrown when an aggregate cannot transition because active child aggregates
 * would be orphaned — e.g. deactivating a Zone that still has ACTIVE Locations,
 * or deactivating a Warehouse that still has ACTIVE Zones.
 *
 * <p>Per {@code specs/contracts/http/master-service-api.md}, this maps to
 * HTTP 409 with error code {@code REFERENCE_INTEGRITY_VIOLATION}. Distinct
 * from {@link InvalidStateTransitionException} (422), which covers invariant
 * violations that concern ONLY the aggregate itself (e.g. deactivating an
 * already-INACTIVE record, or reactivating an EXPIRED Lot).
 */
public class ReferenceIntegrityViolationException extends MasterDomainException {

    public ReferenceIntegrityViolationException(String aggregateType, UUID aggregateId, String reason) {
        super(
                "REFERENCE_INTEGRITY_VIOLATION",
                aggregateType + " " + aggregateId + " cannot be deactivated: " + reason);
    }
}
