package com.example.user.presentation.dto.response;

import com.example.user.application.result.AddressResult;

public record UpdateAddressResponse(String id) {

    public static UpdateAddressResponse from(AddressResult result) {
        return new UpdateAddressResponse(result.id().toString());
    }
}
