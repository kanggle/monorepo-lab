package com.example.scmplatform.logistics.domain.model;

/**
 * The carrier that actually carries the parcel, as resolved by the vendor
 * aggregator (EasyPost selected-rate {@code carrier}, e.g. {@code USPS} / {@code FEDEX}).
 * Framework-free value object.
 */
public record CarrierCode(String value) {

    public CarrierCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("carrierCode must not be blank");
        }
    }

    public static CarrierCode of(String value) {
        return new CarrierCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
