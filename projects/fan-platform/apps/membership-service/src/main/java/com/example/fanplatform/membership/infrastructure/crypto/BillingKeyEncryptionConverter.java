package com.example.fanplatform.membership.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@code AttributeConverter} that encrypts the billing key on the way to the
 * DB and decrypts it on the way back (ADR-002 §D5). Applied via {@code @Convert}
 * on {@code BillingKeyEnrollment#billingKey} → column {@code billing_key_encrypted}.
 *
 * <p>Hibernate instantiates converters with a no-arg constructor and does not
 * dependency-inject them reliably, so the actual crypto is delegated to the
 * Spring-managed {@link BillingKeyEncryptor} singleton via its static accessor —
 * the singleton is constructed at context refresh (before any enrollment row is
 * read/written), so it is always present by the time a convert method runs.
 *
 * <p>{@code autoApply = false}: this converter is opt-in per column (a global
 * {@code String} auto-apply would encrypt every string column).
 */
@Converter(autoApply = false)
public class BillingKeyEncryptionConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return plaintext == null ? null : BillingKeyEncryptor.current().encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BillingKeyEncryptor.current().decrypt(dbData);
    }
}
