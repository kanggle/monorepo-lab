package com.wms.master.domain.model;

/**
 * Functional classification of a {@link Zone}. See
 * {@code specs/services/master-service/domain-model.md} §2 Zone and
 * {@code specs/contracts/http/master-service-api.md} §2 for the authoritative
 * list. Extending this enum requires a contract update first (per CLAUDE.md
 * Contract Rule).
 */
public enum ZoneType {
    AMBIENT,
    CHILLED,
    FROZEN,
    RETURNS,
    BULK,
    PICK
}
