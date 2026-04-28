package com.wms.inventory.domain.exception;

/**
 * Surfaced when a {@code pickingRequestId} or other domain-unique key collides
 * with an existing record whose body differs from the new request. Maps to
 * {@code 409 DUPLICATE_REQUEST}.
 */
public class DuplicateRequestException extends InventoryDomainException {

    public DuplicateRequestException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "DUPLICATE_REQUEST";
    }
}
