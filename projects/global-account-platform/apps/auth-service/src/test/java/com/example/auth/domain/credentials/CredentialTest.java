package com.example.auth.domain.credentials;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ── TASK-BE-329 (ADR-MONO-021 D1): account_type ──────────────────────────

    @Test
    @DisplayName("account_type round-trips through the canonical constructor (OPERATOR)")
    void accountType_roundTrips() {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");
        Credential c = new Credential(
                1L, "acc-op", "acme-corp", "OPERATOR", "op@example.com",
                "hash", "argon2id", now, now, 0);

        assertThat(c.getAccountType()).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("account_type defaults to CONSUMER when absent (null/blank/legacy ctor)")
    void accountType_defaultsToConsumer() {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");

        // null → default
        Credential viaNull = new Credential(
                1L, "acc", "fan-platform", null, "u@example.com", "h", "argon2id", now, now, 0);
        assertThat(viaNull.getAccountType()).isEqualTo("CONSUMER");

        // blank → default
        Credential viaBlank = new Credential(
                1L, "acc", "fan-platform", "  ", "u@example.com", "h", "argon2id", now, now, 0);
        assertThat(viaBlank.getAccountType()).isEqualTo("CONSUMER");

        // legacy (tenant-only) ctor → default
        Credential viaLegacy = new Credential(
                1L, "acc", "fan-platform", "u@example.com", "h", "argon2id", now, now, 0);
        assertThat(viaLegacy.getAccountType()).isEqualTo("CONSUMER");
    }

    @Test
    @DisplayName("account_type is normalized (trim + upper-case)")
    void accountType_normalized() {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");
        Credential c = new Credential(
                1L, "acc", "fan-platform", "  operator ", "u@example.com", "h", "argon2id", now, now, 0);
        assertThat(c.getAccountType()).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("account_type rejects values outside CONSUMER|OPERATOR (contract restriction)")
    void accountType_rejectsInvalid() {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");
        assertThatThrownBy(() -> new Credential(
                1L, "acc", "fan-platform", "ADMIN", "u@example.com", "h", "argon2id", now, now, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONSUMER or OPERATOR");
    }

    @Test
    @DisplayName("create(...) defaults account_type to CONSUMER; explicit overload carries OPERATOR")
    void create_accountTypeFactory() {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");
        CredentialHash hash = CredentialHash.argon2id("h");

        Credential def = Credential.create("acc", "fan-platform", "u@example.com", hash, now);
        assertThat(def.getAccountType()).isEqualTo("CONSUMER");

        Credential op = Credential.create("acc", "acme-corp", "OPERATOR", "u@example.com", hash, now);
        assertThat(op.getAccountType()).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("changePassword preserves account_type")
    void changePassword_preservesAccountType() {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");
        Credential original = new Credential(
                1L, "acc", "acme-corp", "OPERATOR", "op@example.com", "old", "argon2id", now, now, 0);

        Credential updated = original.changePassword(CredentialHash.argon2id("new"), now);

        assertThat(updated.getAccountType()).isEqualTo("OPERATOR");
    }
}
