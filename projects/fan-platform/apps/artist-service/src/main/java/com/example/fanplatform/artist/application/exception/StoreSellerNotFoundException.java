package com.example.fanplatform.artist.application.exception;

/**
 * 422 {@code STORE_SELLER_NOT_FOUND} — the store answered, and it has no seller with
 * that id. Nothing is saved (ADR-MONO-079 D2, TASK-MONO-748 AC-3).
 */
public class StoreSellerNotFoundException extends RuntimeException {

    public StoreSellerNotFoundException(String sellerId) {
        super("Store seller not found: " + sellerId);
    }
}
