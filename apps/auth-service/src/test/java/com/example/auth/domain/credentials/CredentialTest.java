package com.example.auth.domain.credentials;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialTest {

    @Test
    @DisplayName("changePassword replaces hash and algorithm, preserves identity, advances version")
    void changePassword_replacesHash() {
        Instant created = Instant.parse("2026-04-01T00:00:00Z");
        Credential original = new Credential(
                42L,
                "acc-1",
                "alice@example.com",
                "old-hash",
                "argon2id",
                created,
                created,
                3
        );

        Instant changedAt = Instant.parse("2026-04-26T10:00:00Z");
        CredentialHash newHash = new CredentialHash("new-hash", "argon2id");

        Credential updated = original.changePassword(newHash, changedAt);

        // identity preserved
        assertThat(updated.getId()).isEqualTo(42L);
        assertThat(updated.getAccountId()).isEqualTo("acc-1");
        assertThat(updated.getEmail()).isEqualTo("alice@example.com");
        assertThat(updated.getCreatedAt()).isEqualTo(created);

        // hash replaced
        assertThat(updated.getCredentialHash()).isEqualTo("new-hash");
        assertThat(updated.getHashAlgorithm()).isEqualTo("argon2id");

        // bookkeeping
        assertThat(updated.getUpdatedAt()).isEqualTo(changedAt);
        assertThat(updated.getVersion()).isEqualTo(4);

        // original immutable
        assertThat(original.getCredentialHash()).isEqualTo("old-hash");
        assertThat(original.getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("changePassword can carry a different algorithm (lazy migration)")
    void changePassword_withDifferentAlgorithm() {
        Instant created = Instant.parse("2026-04-01T00:00:00Z");
        Credential original = new Credential(
                7L,
                "acc-2",
                "bob@example.com",
                "bcrypt-hash",
                "bcrypt",
                created,
                created,
                0
        );

        Credential updated = original.changePassword(
                CredentialHash.argon2id("argon-hash"), Instant.parse("2026-04-26T10:00:00Z"));

        assertThat(updated.getHashAlgorithm()).isEqualTo("argon2id");
        assertThat(updated.getCredentialHash()).isEqualTo("argon-hash");
        assertThat(updated.getVersion()).isEqualTo(1);
    }
}
