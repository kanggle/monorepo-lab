package com.example.admin.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TotpGeneratorTest {

    /** RFC 6238 Appendix B, ASCII secret "12345678901234567890". */
    private static final byte[] RFC6238_SECRET =
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rfc6238TimeVectorsSha1SixDigits() {
        TotpGenerator gen = new TotpGenerator(new SecureRandom(), Clock.systemUTC());
        // Time values (seconds) and expected 8-digit values per RFC 6238. The
        // 6-digit value is the last 6 digits of the 8-digit expected output.
        // T=59          -> 94287082 -> 287082
        // T=1111111109  -> 07081804 -> 081804
        // T=1111111111  -> 14050471 -> 050471
        // T=1234567890  -> 89005924 -> 005924
        // T=2000000000  -> 69279037 -> 279037
        assertRfcVector(gen, 59L, "287082");
        assertRfcVector(gen, 1111111109L, "081804");
        assertRfcVector(gen, 1111111111L, "050471");
        assertRfcVector(gen, 1234567890L, "005924");
        assertRfcVector(gen, 2000000000L, "279037");
    }

    private static void assertRfcVector(TotpGenerator gen, long timeSeconds, String expectedSixDigit) {
        long counter = timeSeconds / 30L;
        assertThat(gen.code(RFC6238_SECRET, counter)).isEqualTo(expectedSixDigit);
    }

    @Test
    void verifyAcceptsCurrentAndPreviousWindow() {
        // Clock fixed at T=59 → counter = 1. RFC vector for counter=1 => 287082.
        Clock fixed = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        TotpGenerator gen = new TotpGenerator(new SecureRandom(), fixed);
        assertThat(gen.verify(RFC6238_SECRET, "287082")).isTrue();

        // Code for counter=0 must still verify within ±1 window.
        String codeForCounter0 = gen.code(RFC6238_SECRET, 0);
        assertThat(gen.verify(RFC6238_SECRET, codeForCounter0)).isTrue();
    }

    @Test
    void verifyRejectsWrongCode() {
        Clock fixed = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        TotpGenerator gen = new TotpGenerator(new SecureRandom(), fixed);
        assertThat(gen.verify(RFC6238_SECRET, "000000")).isFalse();
    }

    @Test
    void verifyRejectsCodeOutsideWindow() {
        Clock fixed = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        TotpGenerator gen = new TotpGenerator(new SecureRandom(), fixed);
        // Counter 100 is far outside the ±1 window.
        String farCode = gen.code(RFC6238_SECRET, 100);
        assertThat(gen.verify(RFC6238_SECRET, farCode)).isFalse();
    }

    @Test
    void otpauthUriContainsAllExpectedParameters() {
        TotpGenerator gen = new TotpGenerator();
        String uri = gen.otpauthUri(RFC6238_SECRET, "admin-service", "op@example.com");
        assertThat(uri).startsWith("otpauth://totp/admin-service:op%40example.com");
        assertThat(uri).contains("secret=");
        assertThat(uri).contains("issuer=admin-service");
        assertThat(uri).contains("algorithm=SHA1");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }

    @Test
    void newSecretYields20Bytes() {
        TotpGenerator gen = new TotpGenerator();
        assertThat(gen.newSecret()).hasSize(20);
    }

    @Test
    void base32EncodeKnownVector() {
        // RFC 4648 test vector: "foobar" -> MZXW6YTBOI (no padding)
        String encoded = TotpGenerator.base32Encode("foobar".getBytes(StandardCharsets.US_ASCII));
        assertThat(encoded).isEqualTo("MZXW6YTBOI");
    }
}
