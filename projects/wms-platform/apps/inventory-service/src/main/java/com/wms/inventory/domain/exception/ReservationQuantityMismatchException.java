package com.wms.inventory.domain.exception;

import java.util.UUID;

/**
 * The shipped quantity supplied at confirm time does not equal the original
 * reserved quantity for that line. v1 does not support partial shipments —
 * partial shipments must be modeled as release + new picking request.
 */
public class ReservationQuantityMismatchException extends InventoryDomainException {

    public ReservationQuantityMismatchException(UUID lineId, int reserved, int shipped) {
        super("Reservation line " + lineId + " quantity mismatch: reserved="
                + reserved + ", shipped=" + shipped);
    }

    @Override
    public String errorCode() {
        return "RESERVATION_QUANTITY_MISMATCH";
    }
}
