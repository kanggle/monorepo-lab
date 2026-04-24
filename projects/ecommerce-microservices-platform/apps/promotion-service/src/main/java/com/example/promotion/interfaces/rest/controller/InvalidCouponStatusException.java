package com.example.promotion.interfaces.rest.controller;

public class InvalidCouponStatusException extends RuntimeException {

    public InvalidCouponStatusException(String status) {
        super("Invalid coupon status: " + status);
    }
}
