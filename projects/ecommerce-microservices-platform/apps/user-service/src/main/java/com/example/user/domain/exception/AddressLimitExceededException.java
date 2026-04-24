package com.example.user.domain.exception;

public class AddressLimitExceededException extends RuntimeException {

    public AddressLimitExceededException(String message) {
        super(message);
    }
}
