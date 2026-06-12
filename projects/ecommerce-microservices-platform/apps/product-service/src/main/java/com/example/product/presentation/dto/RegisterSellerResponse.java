package com.example.product.presentation.dto;

public record RegisterSellerResponse(String sellerId) {

    public static RegisterSellerResponse from(String sellerId) {
        return new RegisterSellerResponse(sellerId);
    }
}
