package com.example.auth.infrastructure.totp;

import com.example.auth.application.port.TotpCodeCalculator;
import com.example.security.totp.TotpCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * TASK-MONO-771 S2c — {@link TotpCodeCalculator} adapter backed by {@code libs/java-security}'s
 * {@link TotpCodeGenerator} (promoted from admin-service's computation in S2a). The hand-rolled HMAC-SHA1 /
 * RFC 4648 Base32 this class carried in S2b is deleted — this is now a thin delegation, not a second
 * implementation of the RFC.
 *
 * <p>Only the computation is shared. Secret storage (encryption, AAD, key rotation), the ±1 verification window
 * and the anti-replay counter stay in this service, per {@link TotpCodeCalculator}'s javadoc and AC-0 § 3.
 */
@Component
public class Rfc6238TotpCodeCalculator implements TotpCodeCalculator {

    private final TotpCodeGenerator generator = new TotpCodeGenerator();

    @Override
    public byte[] newSecret() {
        return generator.newSecret();
    }

    @Override
    public String codeAt(byte[] secret, long timeStep) {
        return generator.code(secret, timeStep);
    }

    @Override
    public String toBase32(byte[] secret) {
        return TotpCodeGenerator.base32Encode(secret);
    }
}
