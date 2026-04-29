package com.wms.outbound.domain.model;

/**
 * Physical packing-unit type.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §4.
 */
public enum PackingType {
    BOX,
    PALLET,
    ENVELOPE
}
