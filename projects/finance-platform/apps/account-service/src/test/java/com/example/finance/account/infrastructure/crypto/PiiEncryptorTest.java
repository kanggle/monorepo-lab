package com.example.finance.account.infrastructure.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PiiEncryptor} (fintech F7 — AES-256-GCM column
 * encryption): round-trip, tamper detection, key fail-fast.
 */
class PiiEncryptorTest {

    private static final String KEY = "finance-account-test-pii-key-32bytes!";

    @Test
    @DisplayName("F7: round-trip encrypt → decrypt yields the original plaintext")
    void roundTrip() {
        PiiEncryptor enc = new PiiEncryptor(KEY);
        String stored = enc.encryptToString("cust-9b1d4a8c");
        assertThat(stored).startsWith(PiiEncryptor.ACTIVE_KEY_ID + ":");
        assertThat(stored).doesNotContain("cust-9b1d4a8c"); // ciphertext, not plaintext
        assertThat(enc.decryptFromString(stored)).isEqualTo("cust-9b1d4a8c");
    }

    @Test
    @DisplayName("F7: distinct IV per encryption → different ciphertext for same input")
    void distinctIv() {
        PiiEncryptor enc = new PiiEncryptor(KEY);
        assertThat(enc.encryptToString("same"))
                .isNotEqualTo(enc.encryptToString("same"));
    }

    @Test
    @DisplayName("F7: tampered ciphertext fails GCM auth on decrypt")
    void tamperDetected() {
        PiiEncryptor enc = new PiiEncryptor(KEY);
        byte[] envelope = enc.encrypt("secret".getBytes());
        envelope[envelope.length - 1] ^= 0x01; // flip a tag byte
        byte[] tampered = envelope;
        assertThatThrownBy(() -> enc.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt failed");
    }

    @Test
    @DisplayName("Failure Mode #15: missing key fails fast at construction")
    void missingKeyFailsFast() {
        assertThatThrownBy(() -> new PiiEncryptor("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pii-key is required");
    }

    @Test
    @DisplayName("null plaintext encrypts to null (NULL owner_ref tolerated)")
    void nullPassthrough() {
        PiiEncryptor enc = new PiiEncryptor(KEY);
        assertThat(enc.encryptToString(null)).isNull();
        assertThat(enc.decryptFromString(null)).isNull();
    }
}
