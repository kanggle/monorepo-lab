package com.example.admin.infrastructure.security;

import com.example.security.totp.TotpCodeGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;

/**
 * Admin-service's 2FA entry point for RFC 6238 TOTP (HMAC-SHA1, 6 digits,
 * 30s step, &plusmn;1 window).
 *
 * <p>The pure computation was promoted to {@link TotpCodeGenerator} in
 * {@code libs/java-security} by TASK-MONO-771 S2a — this class is now a
 * thin Spring-wired delegate so {@code AdminAuthController} and the rest of
 * this service's wiring (constructor injection, {@code @Mock TotpGenerator}
 * in tests) are unaffected. Behaviour is unchanged: same codes, same
 * window, same otpauth URIs.
 *
 * <p>Per AC-0 §3 of TASK-MONO-771, this break-glass/operator TOTP stays
 * operationally separate from the account-plane TOTP introduced in S2b —
 * only the computation code is shared, never the secret storage, keys, or
 * verification call path.
 *
 * <p>Used by {@code AdminAuthController} for 2FA enroll/verify. The shared
 * secret is stored encrypted (see {@link TotpSecretCipher}); this class
 * operates only on plaintext bytes in memory.
 */
@Component
public class TotpGenerator {

    private final TotpCodeGenerator delegate;

    public TotpGenerator() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    public TotpGenerator(SecureRandom secureRandom, Clock clock) {
        this.delegate = new TotpCodeGenerator(
                Objects.requireNonNull(secureRandom), Objects.requireNonNull(clock));
    }

    /** Produces a fresh 160-bit secret suitable for otpauth enrollment. */
    public byte[] newSecret() {
        return delegate.newSecret();
    }

    /** Current 6-digit TOTP code using the configured clock. */
    public String code(byte[] secret) {
        return delegate.code(secret);
    }

    /** Public for deterministic tests (RFC 6238 time vectors). */
    public String code(byte[] secret, long counter) {
        return delegate.code(secret, counter);
    }

    /** Verifies a 6-digit code against the current counter +/-1 window. */
    public boolean verify(byte[] secret, String code) {
        return delegate.verify(secret, code);
    }

    /**
     * Builds the {@code otpauth://totp/...} URI per Google Authenticator spec.
     * Both {@code issuer} and {@code accountLabel} are URL-encoded.
     */
    public String otpauthUri(byte[] secret, String issuer, String accountLabel) {
        return delegate.otpauthUri(secret, issuer, accountLabel);
    }

    // --- RFC 4648 Base32 (no padding - accepted by Google Authenticator) ----
    public static String base32Encode(byte[] data) {
        return TotpCodeGenerator.base32Encode(data);
    }
}
