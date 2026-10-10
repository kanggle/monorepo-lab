package com.example.admin.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-MONO-772 S2 (R4) — the token shape and the stored hash. */
@DisplayName("OperatorInvitationTokens — 32바이트 토큰 · SHA-256 hex (TASK-MONO-772 S2)")
class OperatorInvitationTokensTest {

    @Test
    @DisplayName("토큰 = base64url 43자(패딩 없음) · 매번 다르다")
    void tokenShape() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String t = OperatorInvitationTokens.newToken();
            assertThat(t).hasSize(43).matches("^[A-Za-z0-9_-]+$");
            assertThat(seen.add(t)).isTrue();
        }
    }

    @Test
    @DisplayName("해시 = SHA-256 hex 64자 (공개 테스트 벡터 'abc')")
    void sha256Vector() {
        assertThat(OperatorInvitationTokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
