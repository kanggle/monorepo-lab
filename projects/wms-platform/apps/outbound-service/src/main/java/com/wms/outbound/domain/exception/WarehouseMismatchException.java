package com.wms.outbound.domain.exception;

/**
 * Raised when an order's lines reference more than one warehouse, or when
 * the order's declared {@code warehouseId} does not match its lines (v1:
 * single-warehouse only). Mapped to {@code 422} with code
 * {@code WAREHOUSE_MISMATCH}.
 */
public class WarehouseMismatchException extends OutboundDomainException {

    public WarehouseMismatchException(String detail) {
        super("Warehouse mismatch: " + detail);
    }

    @Override
    public String errorCode() {
        return "WAREHOUSE_MISMATCH";
    }
}
