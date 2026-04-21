package com.example.user.presentation.dto.response;

import java.util.UUID;

public record CreateAddressResponse(String id) {

    public static CreateAddressResponse from(UUID addressId) {
        return new CreateAddressResponse(addressId.toString());
    }
}
