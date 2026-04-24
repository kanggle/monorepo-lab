package com.example.order.domain.model;

import lombok.Getter;

@Getter
public class ShippingAddress {

    private String recipient;
    private String phone;
    private String zipCode;
    private String address1;
    private String address2;

    private ShippingAddress() {
    }

    public ShippingAddress(String recipient, String phone, String zipCode,
                           String address1, String address2) {
        requireNonBlank(recipient, "recipient");
        requireNonBlank(phone, "phone");
        requireNonBlank(zipCode, "zipCode");
        requireNonBlank(address1, "address1");
        this.recipient = recipient;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    public static ShippingAddress reconstitute(String recipient, String phone, String zipCode,
                                                String address1, String address2) {
        ShippingAddress sa = new ShippingAddress();
        sa.recipient = recipient;
        sa.phone = phone;
        sa.zipCode = zipCode;
        sa.address1 = address1;
        sa.address2 = address2;
        return sa;
    }
}
