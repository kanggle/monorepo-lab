package com.wms.master.domain.model;

/**
 * Functional classification of a {@link Location}. See
 * {@code specs/services/master-service/domain-model.md} §3 Location and
 * {@code specs/contracts/http/master-service-api.md} §3 for the authoritative
 * list. Extending this enum requires a contract update first (per CLAUDE.md
 * Contract Rule).
 */
public enum LocationType {
    STORAGE,
    STAGING_INBOUND,
    STAGING_OUTBOUND,
    DAMAGED,
    QUARANTINE
}
