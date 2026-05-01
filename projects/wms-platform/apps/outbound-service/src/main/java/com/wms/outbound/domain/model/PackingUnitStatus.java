package com.wms.outbound.domain.model;

/**
 * State enum for {@link PackingUnit}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §4.
 *
 * <p>{@link #SEALED} units are immutable: lines cannot be added or removed,
 * and the {@code seal()} domain method rejects double-seal attempts.
 */
public enum PackingUnitStatus {
    OPEN,
    SEALED
}
