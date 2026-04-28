package com.wms.inventory.domain.exception;

/**
 * Master read-model snapshot reports the referenced Location/SKU/Lot is no
 * longer mutable (inactive or expired). Maps to one of:
 * {@code LOCATION_INACTIVE}, {@code SKU_INACTIVE}, {@code LOT_INACTIVE}, or
 * {@code LOT_EXPIRED} — all 422 per the HTTP contract.
 */
public class MasterRefInactiveException extends InventoryDomainException {

    private final String code;

    public MasterRefInactiveException(String code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public String errorCode() {
        return code;
    }

    public static MasterRefInactiveException locationInactive(String locationId) {
        return new MasterRefInactiveException(
                "LOCATION_INACTIVE",
                "Location " + locationId + " is INACTIVE — cannot mutate inventory at this location");
    }

    public static MasterRefInactiveException skuInactive(String skuId) {
        return new MasterRefInactiveException(
                "SKU_INACTIVE",
                "SKU " + skuId + " is INACTIVE — cannot mutate inventory for this SKU");
    }

    public static MasterRefInactiveException lotInactive(String lotId) {
        return new MasterRefInactiveException(
                "LOT_INACTIVE",
                "Lot " + lotId + " is INACTIVE — cannot mutate inventory for this lot");
    }

    public static MasterRefInactiveException lotExpired(String lotId) {
        return new MasterRefInactiveException(
                "LOT_EXPIRED",
                "Lot " + lotId + " is EXPIRED — cannot mutate inventory for this lot");
    }
}
