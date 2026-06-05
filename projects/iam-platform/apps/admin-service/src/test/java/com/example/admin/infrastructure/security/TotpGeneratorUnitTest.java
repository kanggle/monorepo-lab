package com.example.admin.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TotpGenerator 단위 테스트")
class TotpGeneratorUnitTest {

    private static final byte[] FIXED_SECRET =
            {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20};

    private TotpGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TotpGenerator();
    }

    @Test
    @DisplayName("newSecret — 20바이트(160-bit) 시크릿 반환")
    void newSecret_returns20Bytes() {
        assertThat(generator.newSecret()).hasSize(20);
    }

    @Test
    @DisplayName("code — 동일한 시크릿+카운터 → 동일한 코드 (결정론적)")
    void code_sameCounterAndSecret_isDeterministic() {
        String first = generator.code(FIXED_SECRET, 100L);
        String second = generator.code(FIXED_SECRET, 100L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("code — 항상 6자리 숫자 문자열 반환")
    void code_alwaysReturns6DigitString() {
        String code = generator.code(FIXED_SECRET, 0L);

        assertThat(code).matches("\\d{6}");
    }

    @Test
    @DisplayName("verify — 현재 카운터의 코드 → true")
    void verify_currentCode_returnsTrue() {
        Clock fixedClock = Clock.fixed(Instant.ofEpochSecond(100L * 30), ZoneOffset.UTC);
        TotpGenerator fixed = new TotpGenerator(new SecureRandom(), fixedClock);

        String code = fixed.code(FIXED_SECRET);

        assertThat(fixed.verify(FIXED_SECRET, code)).isTrue();
    }

    @Test
    @DisplayName("verify — 잘못된 길이의 코드 → false")
    void verify_wrongLengthCode_returnsFalse() {
        assertThat(generator.verify(FIXED_SECRET, "12345")).isFalse();
    }

    @Test
    @DisplayName("otpauthUri — otpauth://totp 형식 및 필수 파라미터 포함")
    void otpauthUri_containsRequiredComponents() {
        String uri = generator.otpauthUri(FIXED_SECRET, "AdminService", "alice@example.com");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=");
        assertThat(uri).contains("issuer=AdminService");
        assertThat(uri).contains("algorithm=SHA1");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }
}
