package com.example.promotion.application.exception;

public class InvalidCouponStatusException extends RuntimeException {

    public InvalidCouponStatusException(String status) {
        super("Invalid coupon status: " + status);
    }
}
