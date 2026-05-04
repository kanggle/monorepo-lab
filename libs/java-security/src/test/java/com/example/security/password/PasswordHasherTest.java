package com.example.security.password;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHasherTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() {
        // Use low parameters for fast tests
        hasher = new Argon2idPasswordHasher(
                4096,   // memoryKb (low for test speed)
                1,      // iterations
                1,      // parallelism
                32,     // outputLength
                16      // saltLength
        );
    }

    @Test
    @DisplayName("hash produces argon2id formatted string")
    void hashProducesArgon2idFormat() {
        String hashed = hasher.hash("myPassword123!");
        assertThat(hashed).startsWith("$argon2id$");
    }

    @Test
    @DisplayName("hash + verify roundtrip succeeds for correct password")
    void hashAndVerifyRoundtripSuccess() {
        String plain = "correctPassword!1";
        String hashed = hasher.hash(plain);

        assertThat(hasher.verify(plain, hashed)).isTrue();
    }

    @Test
    @DisplayName("verify fails for wrong password")
    void verifyFailsForWrongPassword() {
        String hashed = hasher.hash("correctPassword!1");

        assertThat(hasher.verify("wrongPassword!1", hashed)).isFalse();
    }

    @Test
    @DisplayName("hash produces different outputs for same input (random salt)")
    void hashProducesDifferentOutputsForSameInput() {
        String plain = "samePassword!1";
        String hash1 = hasher.hash(plain);
        String hash2 = hasher.hash(plain);

        assertThat(hash1).isNotEqualTo(hash2);
        // Both should still verify
        assertThat(hasher.verify(plain, hash1)).isTrue();
        assertThat(hasher.verify(plain, hash2)).isTrue();
    }

    @Test
    @DisplayName("hash throws on null input")
    void hashThrowsOnNull() {
        assertThatThrownBy(() -> hasher.hash(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("hash throws on empty input")
    void hashThrowsOnEmpty() {
        assertThatThrownBy(() -> hasher.hash(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("explicit saltLength constructor produces verifiable hash")
    void explicitSaltLengthConstructorProducesVerifiableHash() {
        PasswordHasher customHasher = new Argon2idPasswordHasher(4096, 1, 1, 32, 16);
        String plain = "testPassword!1";
        String hashed = customHasher.hash(plain);

        assertThat(hashed).startsWith("$argon2id$");
        assertThat(customHasher.verify(plain, hashed)).isTrue();
    }

    @Test
    @DisplayName("constructor throws on non-positive saltLength")
    void constructorThrowsOnNonPositiveSaltLength() {
        assertThatThrownBy(() -> new Argon2idPasswordHasher(4096, 1, 1, 32, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("saltLength");

        assertThatThrownBy(() -> new Argon2idPasswordHasher(4096, 1, 1, 32, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("saltLength");
    }

    @Test
    @DisplayName("default constructor uses spec-compliant parameters")
    void defaultConstructorUsesSpecCompliantParameters() {
        PasswordHasher defaultHasher = new Argon2idPasswordHasher();
        String plain = "specTest!1";
        String hashed = defaultHasher.hash(plain);

        assertThat(hashed).startsWith("$argon2id$");
        assertThat(defaultHasher.verify(plain, hashed)).isTrue();
    }
}
