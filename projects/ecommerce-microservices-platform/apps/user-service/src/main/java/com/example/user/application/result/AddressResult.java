package com.example.user.application.result;

import com.example.user.domain.model.Address;

import java.util.UUID;

public record AddressResult(
        UUID id,
        String label,
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2,
        boolean isDefault
) {
    public static AddressResult from(Address address) {
        return new AddressResult(
                address.getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhone(),
                address.getZipCode(),
                address.getAddress1(),
                address.getAddress2(),
                address.isDefault()
        );
    }
}
