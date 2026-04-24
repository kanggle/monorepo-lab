package com.example.product.domain.exception;

public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException(String objectKey) {
        super("Media not found in storage: " + objectKey);
    }
}
