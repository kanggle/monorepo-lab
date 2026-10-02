package com.example.fanplatform.artist.application.exception;

/**
 * 422 {@code STORE_SELLER_CLOSED} — the store answered, and the seller is CLOSED
 * (terminal). Nothing is saved (ADR-MONO-079 D2, TASK-MONO-748 AC-3).
 */
public class StoreSellerClosedException extends RuntimeException {

    public StoreSellerClosedException(String sellerId) {
        super("Store seller is closed: " + sellerId);
    }
}
