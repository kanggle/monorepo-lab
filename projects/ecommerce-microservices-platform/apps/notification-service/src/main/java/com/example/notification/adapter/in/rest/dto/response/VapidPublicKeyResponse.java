package com.example.notification.adapter.in.rest.dto.response;

public record VapidPublicKeyResponse(String publicKey) {
    public static VapidPublicKeyResponse of(String publicKey) {
        return new VapidPublicKeyResponse(publicKey);
    }
}
