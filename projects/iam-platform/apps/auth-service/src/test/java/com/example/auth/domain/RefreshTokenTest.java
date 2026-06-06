package com.example.auth.domain;

import com.example.auth.domain.token.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    @DisplayName("New token is not revoked and not expired")
    void newTokenIsValid() {
        RefreshToken token = RefreshToken.create(
                "jti-1", "acc-1", Instant.now(),
                Instant.now().plusSeconds(604800), null, "fp-1"
        );

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isExpired()).isFalse();
        assertThat(token.getRotatedFrom()).isNull();
    }

    @Test
    @DisplayName("Expired token reports as expired")
    void expiredToken() {
        RefreshToken token = RefreshToken.create(
                "jti-1", "acc-1", Instant.now().minusSeconds(700000),
                Instant.now().minusSeconds(1), null, "fp-1"
        );

        assertThat(token.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Revoke marks token as revoked")
    void revokeToken() {
        RefreshToken token = RefreshToken.create(
                "jti-1", "acc-1", Instant.now(),
                Instant.now().plusSeconds(604800), null, "fp-1"
        );

        token.revoke();
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Rotated token has rotatedFrom set")
    void rotatedToken() {
        RefreshToken token = RefreshToken.create(
                "jti-2", "acc-1", Instant.now(),
                Instant.now().plusSeconds(604800), "jti-1", "fp-1"
        );

        assertThat(token.getRotatedFrom()).isEqualTo("jti-1");
    }
}
