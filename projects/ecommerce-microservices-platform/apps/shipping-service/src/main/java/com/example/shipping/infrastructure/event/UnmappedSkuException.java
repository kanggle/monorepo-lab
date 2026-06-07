package com.example.shipping.infrastructure.event;

/**
 * Raised by {@link FulfillmentAcl} when {@code fulfillment.require-sku-mapping=true}
 * and an order line carries a SKU absent from the configured SKU map. The order's
 * fulfillment event is NOT published (no silent drop — caller logs + alerts).
 */
public class UnmappedSkuException extends RuntimeException {

    public UnmappedSkuException(String message) {
        super(message);
    }
}
