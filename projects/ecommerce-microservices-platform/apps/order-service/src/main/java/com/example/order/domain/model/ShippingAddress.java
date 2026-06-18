package com.example.order.domain.model;

import lombok.Getter;

@Getter
public class ShippingAddress {

    /**
     * Tombstone value written into the identifying PII fields when an order's
     * shipping-address snapshot is anonymized in reaction to an IAM
     * {@code account.deleted(anonymized=true)} (ADR-MONO-037 P3-B, the standing
     * TASK-BE-258 GDPR consumer obligation for the order store). The identifying
     * PII fields ({@code recipient}, {@code phone}, {@code address1}) AND the
     * NOT-NULL {@code zipCode} ({@code orders.zip_code VARCHAR(20) NOT NULL}) are
     * overwritten with this tombstone (not nulled) so every NOT-NULL column stays
     * satisfied on flush while carrying no personal information. The only field
     * cleared to {@code null} is the nullable {@code address2}.
     */
    public static final String ANONYMIZED_TOMBSTONE = "[deleted]";

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

    /**
     * A PII-anonymized copy of this address (ADR-MONO-037 P3-B). The identifying
     * fields ({@code recipient}, {@code phone}, {@code address1}) and the NOT-NULL
     * {@code zipCode} are replaced with {@link #ANONYMIZED_TOMBSTONE} (preserving
     * the {@code orders.zip_code VARCHAR(20) NOT NULL} constraint on flush without
     * personal data); the nullable {@code address2} is cleared to {@code null}.
     * Idempotent: anonymizing an already-anonymized address yields an equivalent
     * tombstoned address.
     */
    public ShippingAddress anonymized() {
        return ShippingAddress.reconstitute(
                ANONYMIZED_TOMBSTONE, ANONYMIZED_TOMBSTONE, ANONYMIZED_TOMBSTONE,
                ANONYMIZED_TOMBSTONE, null);
    }

    /** Whether every identifying PII field and the {@code zipCode} already hold the anonymization tombstone. */
    public boolean isAnonymized() {
        return ANONYMIZED_TOMBSTONE.equals(recipient)
                && ANONYMIZED_TOMBSTONE.equals(phone)
                && ANONYMIZED_TOMBSTONE.equals(zipCode)
                && ANONYMIZED_TOMBSTONE.equals(address1);
    }
}
