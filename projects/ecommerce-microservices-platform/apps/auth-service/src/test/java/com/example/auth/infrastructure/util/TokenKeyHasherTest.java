package com.example.auth.infrastructure.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("TokenKeyHasher 단위 테스트")
class TokenKeyHasherTest {

    @Test
    @DisplayName("동일 입력은 동일 해시를 반환한다")
    void sameInput_sameHash() {
        String input = "test-token-value";

        String hash1 = TokenKeyHasher.sha256Hex(input);
        String hash2 = TokenKeyHasher.sha256Hex(input);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("서로 다른 입력은 다른 해시를 반환한다")
    void differentInput_differentHash() {
        String hash1 = TokenKeyHasher.sha256Hex("token-a");
        String hash2 = TokenKeyHasher.sha256Hex("token-b");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("SHA-256 해시는 64자 16진수 문자열이다")
    void hash_is64CharHexString() {
        String hash = TokenKeyHasher.sha256Hex("any-input");

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("알려진 입력에 대해 올바른 SHA-256 해시를 반환한다")
    void knownInput_correctHash() {
        // echo -n "" | sha256sum → e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String hash = TokenKeyHasher.sha256Hex("");

        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("null 입력 시 NullPointerException을 던진다")
    void nullInput_throwsNullPointerException() {
        assertThatNullPointerException()
            .isThrownBy(() -> TokenKeyHasher.sha256Hex(null))
            .withMessage("input must not be null");
    }
}
