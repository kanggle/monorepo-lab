package com.example.scmplatform.logistics.domain.model;

/**
 * Carrier tracking number returned by the vendor on a successful dispatch
 * (EasyPost {@code tracking_code}). Framework-free value object.
 */
public record TrackingNo(String value) {

    public TrackingNo {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("trackingNo must not be blank");
        }
    }

    public static TrackingNo of(String value) {
        return new TrackingNo(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
