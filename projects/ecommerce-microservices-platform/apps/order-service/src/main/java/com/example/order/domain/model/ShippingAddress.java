package com.example.order.domain.model;

import lombok.Getter;

@Getter
public class ShippingAddress {

    /**
     * Tombstone value written into the identifying PII fields when an order's
     * shipping-address snapshot is anonymized in reaction to an IAM
     * {@code account.deleted(anonymized=true)} (ADR-MONO-037 P3-B, the standing
     * TASK-BE-258 GDPR consumer obligation for the order store). Identifying fields
     * are overwritten (not nulled) so any NOT-NULL column stays satisfied while
     * carrying no personal information; non-identifying structural fields
     * ({@code zipCode}, {@code address2}) are cleared.
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
     * fields ({@code recipient}, {@code phone}, {@code address1}) are replaced with
     * {@link #ANONYMIZED_TOMBSTONE} (preserving any NOT-NULL constraint without
     * personal data); the non-identifying structural fields ({@code zipCode},
     * {@code address2}) are cleared. Idempotent: anonymizing an already-anonymized
     * address yields an equivalent tombstoned address.
     */
    public ShippingAddress anonymized() {
        return ShippingAddress.reconstitute(
                ANONYMIZED_TOMBSTONE, ANONYMIZED_TOMBSTONE, null, ANONYMIZED_TOMBSTONE, null);
    }

    /** Whether every identifying PII field already holds the anonymization tombstone. */
    public boolean isAnonymized() {
        return ANONYMIZED_TOMBSTONE.equals(recipient)
                && ANONYMIZED_TOMBSTONE.equals(phone)
                && ANONYMIZED_TOMBSTONE.equals(address1);
    }
}
