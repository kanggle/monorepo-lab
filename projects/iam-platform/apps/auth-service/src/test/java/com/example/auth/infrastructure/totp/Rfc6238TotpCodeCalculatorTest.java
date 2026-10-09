package com.example.auth.infrastructure.totp;

import com.example.auth.application.port.TotpCodeCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-771 S2c — the {@code libs/java-security}-backed adapter, pinned by RFC 6238 Appendix B (SHA-1 seed
 * {@code "12345678901234567890"}; the 8-digit vectors truncated to the 6 digits this IdP uses = the same value mod
 * 10^6). 🔵 These are the same vectors the S2b local implementation was pinned by — proving the swap to
 * {@link com.example.security.totp.TotpCodeGenerator} changed nothing observable.
 */
@DisplayName("RFC 6238 TOTP 계산 (TASK-MONO-771 S2b)")
class Rfc6238TotpCodeCalculatorTest {

    private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final Rfc6238TotpCodeCalculator calculator = new Rfc6238TotpCodeCalculator();

    @ParameterizedTest(name = "T={0} → {1}")
    @CsvSource({
            "59, 287082",
            "1111111109, 081804",
            "1111111111, 050471",
            "1234567890, 005924",
            "2000000000, 279037",
            "20000000000, 353130"
    })
    @DisplayName("RFC 6238 부록 B SHA-1 벡터(6자리)")
    void rfcVectors(long epochSeconds, String expected) {
        long step = TotpCodeCalculator.timeStepOf(Instant.ofEpochSecond(epochSeconds));
        assertThat(calculator.codeAt(RFC_SEED, step)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Base32(RFC 4648, 패딩 없음) — 시드의 알려진 인코딩")
    void base32() {
        assertThat(calculator.toBase32(RFC_SEED)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test
    @DisplayName("새 비밀 = 160bit, 매번 다르다")
    void newSecret() {
        byte[] a = calculator.newSecret();
        assertThat(a).hasSize(20);
        assertThat(a).isNotEqualTo(calculator.newSecret());
    }
}
