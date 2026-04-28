package com.wms.inventory.domain.exception;

public class ReservationNotFoundException extends InventoryDomainException {

    public ReservationNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "RESERVATION_NOT_FOUND";
    }
}
