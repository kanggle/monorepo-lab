package com.example.user.presentation.dto.response;

import com.example.user.application.result.AddressResult;

import java.util.List;

public record AddressListResponse(List<AddressResponse> addresses) {

    public static AddressListResponse from(List<AddressResult> results) {
        return new AddressListResponse(
                results.stream().map(AddressResponse::from).toList()
        );
    }

    public record AddressResponse(
            String id,
            String label,
            String recipientName,
            String phone,
            String zipCode,
            String address1,
            String address2,
            boolean isDefault
    ) {
        public static AddressResponse from(AddressResult result) {
            return new AddressResponse(
                    result.id().toString(),
                    result.label(),
                    result.recipientName(),
                    result.phone(),
                    result.zipCode(),
                    result.address1(),
                    result.address2(),
                    result.isDefault()
            );
        }
    }
}
