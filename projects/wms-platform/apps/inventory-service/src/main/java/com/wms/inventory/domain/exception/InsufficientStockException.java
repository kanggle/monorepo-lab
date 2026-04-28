package com.wms.inventory.domain.exception;

import com.wms.inventory.domain.model.Bucket;
import java.util.UUID;

/**
 * Thrown when a domain operation would drive a bucket below zero. Maps to
 * {@code 422 INSUFFICIENT_STOCK} per the HTTP contract.
 */
public class InsufficientStockException extends InventoryDomainException {

    private final UUID inventoryId;
    private final Bucket bucket;
    private final int available;
    private final int requested;

    public InsufficientStockException(UUID inventoryId, Bucket bucket, int available, int requested) {
        super("Insufficient stock for inventory " + inventoryId + " bucket " + bucket
                + ": available=" + available + ", requested=" + requested);
        this.inventoryId = inventoryId;
        this.bucket = bucket;
        this.available = available;
        this.requested = requested;
    }

    @Override
    public String errorCode() {
        return "INSUFFICIENT_STOCK";
    }

    public UUID inventoryId() {
        return inventoryId;
    }

    public Bucket bucket() {
        return bucket;
    }

    public int available() {
        return available;
    }

    public int requested() {
        return requested;
    }
}
