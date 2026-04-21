package com.example.user.domain.model;

import com.example.user.domain.exception.AddressLimitExceededException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class Address {

    public static final int MAX_ADDRESSES_PER_USER = 10;
    public static final int MAX_LABEL_LENGTH = 50;
    public static final int MAX_RECIPIENT_NAME_LENGTH = 50;
    public static final int MAX_PHONE_LENGTH = 20;
    public static final int MAX_ZIP_CODE_LENGTH = 10;
    public static final int MAX_ADDRESS1_LENGTH = 255;
    public static final int MAX_ADDRESS2_LENGTH = 255;

    private UUID id;
    private UUID userId;
    private String label;
    private String recipientName;
    private String phone;
    private String zipCode;
    private String address1;
    private String address2;
    private boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;

    private Address() {
    }

    public static Address create(UUID userId, String label, String recipientName, String phone,
                                  String zipCode, String address1, String address2, boolean isDefault) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        Address address = new Address();
        address.id = UUID.randomUUID();
        address.userId = userId;
        address.label = validateAndTrimRequired(label, MAX_LABEL_LENGTH, "Label");
        address.recipientName = validateAndTrimRequired(recipientName, MAX_RECIPIENT_NAME_LENGTH, "Recipient name");
        address.phone = validateAndTrimRequired(phone, MAX_PHONE_LENGTH, "Phone");
        address.zipCode = validateAndTrimRequired(zipCode, MAX_ZIP_CODE_LENGTH, "Zip code");
        address.address1 = validateAndTrimRequired(address1, MAX_ADDRESS1_LENGTH, "Address1");
        address.address2 = address2 == null ? null : validateAndTrimOptional(address2, MAX_ADDRESS2_LENGTH, "Address2");
        address.isDefault = isDefault;
        Instant now = Instant.now();
        address.createdAt = now;
        address.updatedAt = now;
        return address;
    }

    public static Address reconstitute(UUID id, UUID userId, String label, String recipientName,
                                        String phone, String zipCode, String address1, String address2,
                                        boolean isDefault, Instant createdAt, Instant updatedAt) {
        Address address = new Address();
        address.id = id;
        address.userId = userId;
        address.label = label;
        address.recipientName = recipientName;
        address.phone = phone;
        address.zipCode = zipCode;
        address.address1 = address1;
        address.address2 = address2;
        address.isDefault = isDefault;
        address.createdAt = createdAt;
        address.updatedAt = updatedAt;
        return address;
    }

    public void update(String label, String recipientName, String phone,
                       String zipCode, String address1, String address2, Boolean isDefault) {
        if (label != null) {
            this.label = validateAndTrimRequired(label, MAX_LABEL_LENGTH, "Label");
        }
        if (recipientName != null) {
            this.recipientName = validateAndTrimRequired(recipientName, MAX_RECIPIENT_NAME_LENGTH, "Recipient name");
        }
        if (phone != null) {
            this.phone = validateAndTrimRequired(phone, MAX_PHONE_LENGTH, "Phone");
        }
        if (zipCode != null) {
            this.zipCode = validateAndTrimRequired(zipCode, MAX_ZIP_CODE_LENGTH, "Zip code");
        }
        if (address1 != null) {
            this.address1 = validateAndTrimRequired(address1, MAX_ADDRESS1_LENGTH, "Address1");
        }
        if (address2 != null) {
            this.address2 = validateAndTrimOptional(address2, MAX_ADDRESS2_LENGTH, "Address2");
        }
        if (isDefault != null) {
            this.isDefault = isDefault;
        }
        this.updatedAt = Instant.now();
    }

    public static void validateAddressLimit(int currentCount) {
        if (currentCount >= MAX_ADDRESSES_PER_USER) {
            throw new AddressLimitExceededException(
                    "Maximum number of addresses reached (" + MAX_ADDRESSES_PER_USER + ")"
            );
        }
    }

    private static void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static void validateFieldLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
    }

    private static String validateAndTrimRequired(String value, int maxLength, String fieldName) {
        validateNotBlank(value, fieldName);
        String trimmed = value.trim();
        validateFieldLength(trimmed, maxLength, fieldName);
        return trimmed;
    }

    private static String validateAndTrimOptional(String value, int maxLength, String fieldName) {
        String trimmed = value.trim();
        validateFieldLength(trimmed, maxLength, fieldName);
        return trimmed;
    }

}
