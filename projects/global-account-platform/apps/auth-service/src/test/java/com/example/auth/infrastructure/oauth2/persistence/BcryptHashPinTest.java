package com.example.auth.infrastructure.oauth2.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin test for the BCrypt hashes shipped in Flyway seed migrations
 * V0008–V0013 ({@code oauth_clients.client_secret_hash} column).
 *
 * <p>Exists because TASK-MONO-044c discovered that V0008 + V0011 carried
 * BCrypt strings that <em>looked</em> like valid hashes but did not actually
 * {@link BCryptPasswordEncoder#matches} their commented-as-verified password.
 * The bug was silent (comment-only "verified" claim) and broke every
 * {@code client_credentials} grant against {@code test-internal-client} +
 * {@code fan-platform-user-flow-client}, masked behind the libs/java-web
 * servlet leak that was clearing CI Job 1 first.
 *
 * <p>This test runs in the unit (non-Testcontainers) lane. If anyone re-rolls
 * one of the seed hashes without verifying it through {@code matches}, this
 * test fails immediately and traps the regression at its source.
 */
class BcryptHashPinTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Test
    @DisplayName("V0008/V0009/V0010 'secret' hash round-trips through BCrypt.matches")
    void v0008SecretHashMatches() {
        // V0008 test-internal-client + demo-spa-client (NULL),
        // V0009 community-service-client + membership-service-client,
        // V0010 wms-user-flow-client + wms-internal-services-client
        String hash = "$2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS";
        assertThat(ENCODER.matches("secret", hash))
                .as("BCrypt hash for 'secret' must round-trip; "
                        + "if this fails, regenerate via BCryptPasswordEncoder.encode(\"secret\") "
                        + "and update V0008 / V0009 / V0010 + this pin.")
                .isTrue();
    }

    @Test
    @DisplayName("V0011 'fan-platform-dev' hash round-trips through BCrypt.matches")
    void v0011FanPlatformDevHashMatches() {
        // V0011 fan-platform-user-flow-client
        String hash = "$2a$10$TqHKIHozp9LhJTBLZHrOkusLbXZ/z12IWiCl51O665gJtMIc3UvuO";
        assertThat(ENCODER.matches("fan-platform-dev", hash))
                .as("BCrypt hash for 'fan-platform-dev' must round-trip; "
                        + "if this fails, regenerate via BCryptPasswordEncoder.encode(\"fan-platform-dev\") "
                        + "and update V0011 + this pin.")
                .isTrue();
    }

    @Test
    @DisplayName("V0012 'ecommerce-dev' hash round-trips through BCrypt.matches")
    void v0012EcommerceDevHashMatches() {
        // V0012 ecommerce-web-store-client + ecommerce-admin-dashboard-client
        // (kept here so the pin set covers every seed migration uniformly)
        String hash = "$2a$10$M1zIY5Ieur41YpAmsfuy0u8UADvbaiVWcPiPJXnR1exRpgNCHW2rm";
        assertThat(ENCODER.matches("ecommerce-dev", hash))
                .as("BCrypt hash for 'ecommerce-dev' must round-trip")
                .isTrue();
    }

    @Test
    @DisplayName("V0013 'scm-dev' hash round-trips through BCrypt.matches")
    void v0013ScmDevHashMatches() {
        // V0013 scm-platform-internal-services-client
        String hash = "$2a$10$Eck9mC32OSo1eicVmzvI/.T8ChCycZv8X6VB/HbCJxUvqFmVY5fim";
        assertThat(ENCODER.matches("scm-dev", hash))
                .as("BCrypt hash for 'scm-dev' must round-trip")
                .isTrue();
    }
}
