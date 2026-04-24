package com.example.shipping.domain.exception;

import com.example.shipping.domain.model.ShippingStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ShippingStatus from, ShippingStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
