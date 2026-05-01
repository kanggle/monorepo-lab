package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when a {@code PackingUnit} id cannot be located. Mapped to {@code 404}
 * with code {@code PACKING_UNIT_NOT_FOUND}.
 */
public class PackingUnitNotFoundException extends OutboundDomainException {

    private final UUID packingUnitId;

    public PackingUnitNotFoundException(UUID packingUnitId) {
        super("Packing unit not found: " + packingUnitId);
        this.packingUnitId = packingUnitId;
    }

    public UUID getPackingUnitId() {
        return packingUnitId;
    }

    @Override
    public String errorCode() {
        return "PACKING_UNIT_NOT_FOUND";
    }
}
