package com.example.order.application.exception;

/**
 * The coupon in a placement request cannot be applied (TASK-INT-026) — promotion-service refused
 * it, or its discount would leave less than 1 KRW to pay. {@link #getCode()} is the error code the
 * client receives with {@code 422}.
 */
public class CouponRejectedException extends RuntimeException {

    private final String code;

    public CouponRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
