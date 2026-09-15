package com.example.order.application.exception;

/**
 * promotion-service did not answer a coupon apply — timeout, 5xx, connection failure or an open
 * circuit (TASK-INT-026). The placement fails rather than proceeding without the discount.
 */
public class CouponServiceUnavailableException extends RuntimeException {

    public CouponServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
