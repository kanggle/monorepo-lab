package com.example.fanplatform.membership.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Column-level AES-GCM encryption for the stored billing key (ADR-002 §D5). A
 * billing key is not a card number, but it is a durable capability to charge on
 * the owner's behalf, so it is treated as secret-grade: encrypted at rest, never
 * logged, never returned in a DTO.
 *
 * <p><b>Envelope:</b> {@code base64( [12-byte IV] || ciphertext || [16-byte GCM tag] )}.
 * A fresh random IV per encryption; the GCM tag (integrity) is appended by the
 * cipher on finalize. Stored as base64 text so it fits the {@code TEXT} column.
 *
 * <p><b>Key:</b> a base64-encoded, exactly-32-byte (AES-256) key read from
 * {@code fanplatform.membership.billing-key.encryption-key} — env-injected in
 * production ({@code FAN_MEMBERSHIP_BILLING_KEY_ENCRYPTION_KEY}), never committed
 * (the committed default in {@code application.yml} is a throwaway DEV key, same
 * posture as the PortOne API secret). A malformed / wrong-length key fails the
 * constructor self-test, so misconfiguration fails fast at boot rather than
 * silently corrupting stored keys.
 *
 * <p>A single Spring singleton is published to {@link BillingKeyEncryptionConverter}
 * via a static holder — a JPA {@code AttributeConverter} is instantiated by
 * Hibernate (no-arg) and cannot itself be dependency-injected reliably across
 * Hibernate versions; the static hand-off avoids depending on the Hibernate
 * bean-container wiring. Only the money-write path decrypts.
 *
 * <p>{@code final} so the constructor self-test's virtual calls cannot be observed
 * by an unfinished subclass ({@code [this-escape]}); this is a crypto component,
 * subclassing is not part of its contract.
 */
@Component
public final class BillingKeyEncryptor {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** Published for the Hibernate-instantiated converter. Set once at bean construction. */
    private static volatile BillingKeyEncryptor instance;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public BillingKeyEncryptor(
            @Value("${fanplatform.membership.billing-key.encryption-key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "fanplatform.membership.billing-key.encryption-key is required");
        }
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "billing-key.encryption-key must be valid base64", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "billing-key.encryption-key must decode to exactly 32 bytes (AES-256), got "
                            + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        // Boot self-test: round-trip a probe to fail-fast on misconfiguration.
        String probe = "billing-key-encryptor-self-test";
        if (!probe.equals(decrypt(encrypt(probe)))) {
            throw new IllegalStateException("BillingKeyEncryptor self-test failed");
        }
        instance = this;
    }

    /** @return the singleton, or throw if the context has not initialised it yet. */
    static BillingKeyEncryptor current() {
        BillingKeyEncryptor i = instance;
        if (i == null) {
            throw new IllegalStateException("BillingKeyEncryptor is not initialised");
        }
        return i;
    }

    /** Encrypt a plaintext billing key to the base64 envelope stored at rest. */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(IV_LENGTH + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            // Never include the plaintext in the message.
            throw new IllegalStateException("billing key encrypt failed", e);
        }
    }

    /** Decrypt the base64 envelope back to the plaintext billing key. */
    public String decrypt(String envelope) {
        try {
            byte[] all = Base64.getDecoder().decode(envelope);
            if (all.length <= IV_LENGTH) {
                throw new IllegalArgumentException("envelope too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            System.arraycopy(all, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("billing key decrypt failed", e);
        }
    }
}
