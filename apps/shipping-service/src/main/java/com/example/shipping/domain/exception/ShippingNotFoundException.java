package com.example.shipping.domain.exception;

public class ShippingNotFoundException extends RuntimeException {

    public ShippingNotFoundException(String identifier) {
        super("Shipping record not found: " + identifier);
    }
}
