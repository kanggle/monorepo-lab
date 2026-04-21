package com.example.promotion.interfaces.rest.controller;

public class InvalidPromotionStatusException extends RuntimeException {

    public InvalidPromotionStatusException(String status) {
        super("Invalid promotion status: " + status);
    }
}
