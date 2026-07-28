package com.example.fanplatform.membership.infrastructure.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the billing-key AES-GCM encryptor + its JPA converter delegate.
 */
class BillingKeyEncryptorTest {

    // A base64 32-byte key (AES-256).
    private static final String KEY_B64 = "AwoRGB8mLTQ7QklQV15lbHN6gYiPlp2kq7K5wMfO1dw=";

    @Test
    @DisplayName("encrypt → decrypt round-trips the plaintext")
    void roundTrip() {
        BillingKeyEncryptor enc = new BillingKeyEncryptor(KEY_B64);
        String plain = "billing_key_opaque_vendor_value_123";
        String cipher = enc.encrypt(plain);

        assertThat(cipher).isNotEqualTo(plain);
        assertThat(cipher).doesNotContain(plain);
        assertThat(enc.decrypt(cipher)).isEqualTo(plain);
    }

    @Test
    @DisplayName("a fresh random IV makes two encryptions of the same plaintext differ")
    void nonDeterministicCiphertext() {
        BillingKeyEncryptor enc = new BillingKeyEncryptor(KEY_B64);
        assertThat(enc.encrypt("same")).isNotEqualTo(enc.encrypt("same"));
    }

    @Test
    @DisplayName("a tampered ciphertext (GCM tag mismatch) fails to decrypt")
    void tamperRejected() {
        BillingKeyEncryptor enc = new BillingKeyEncryptor(KEY_B64);
        String cipher = enc.encrypt("secret");
        // Flip the last base64 char to corrupt the GCM tag.
        char last = cipher.charAt(cipher.length() - 1);
        String tampered = cipher.substring(0, cipher.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> enc.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a non-32-byte key fails fast at construction")
    void wrongLengthKeyRejected() {
        // base64 of 16 bytes, not 32.
        assertThatThrownBy(() -> new BillingKeyEncryptor("AAAAAAAAAAAAAAAAAAAAAA=="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("converter delegates to the singleton encryptor and round-trips")
    void converterRoundTrip() {
        // Constructing the encryptor publishes the static singleton the converter uses.
        new BillingKeyEncryptor(KEY_B64);
        BillingKeyEncryptionConverter converter = new BillingKeyEncryptionConverter();

        String stored = converter.convertToDatabaseColumn("bk_abc");
        assertThat(stored).isNotNull().isNotEqualTo("bk_abc");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("bk_abc");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
