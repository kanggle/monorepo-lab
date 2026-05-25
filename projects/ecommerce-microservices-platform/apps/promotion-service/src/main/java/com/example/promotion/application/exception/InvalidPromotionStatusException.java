package com.example.promotion.application.exception;

public class InvalidPromotionStatusException extends RuntimeException {

    public InvalidPromotionStatusException(String status) {
        super("Invalid promotion status: " + status);
    }
}
