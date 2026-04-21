package com.example.product.presentation.dto;

import java.util.UUID;

public record RegisterProductResponse(String id) {

    public static RegisterProductResponse from(UUID id) {
        return new RegisterProductResponse(id.toString());
    }
}
