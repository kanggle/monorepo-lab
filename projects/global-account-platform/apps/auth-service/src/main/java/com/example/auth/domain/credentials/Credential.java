package com.example.auth.domain.credentials;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a user's credential (password hash).
 * Pure POJO - no framework annotations.
 *
 * <p>As of TASK-BE-063, the credential entity also carries the login email so
 * auth-service can resolve email → credential locally without a cross-service
 * round trip. The canonical source of email truth remains account-service; this
 * is a minimal denormalized copy required for login.</p>
 *
 * <p>As of TASK-BE-229, the credential also carries {@code tenantId} so that
 * tenant-scoped login lookup is possible without a cross-service call.
 * (specs/features/multi-tenancy.md §Isolation Strategy)</p>
 */
public class Credential {

    private final Long id;
    private final String accountId;
    private final String tenantId;
    private final String email;
    private final String credentialHash;
    private final String hashAlgorithm;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final int version;

    public Credential(Long id, String accountId, String tenantId, String email,
                      String credentialHash, String hashAlgorithm,
                      Instant createdAt, Instant updatedAt, int version) {
        this.id = id;
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.credentialHash = Objects.requireNonNull(credentialHash, "credentialHash must not be null");
        this.hashAlgorithm = Objects.requireNonNull(hashAlgorithm, "hashAlgorithm must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.version = version;
    }

    /**
     * @deprecated Use {@link #Credential(Long, String, String, String, String, String, Instant, Instant, int)}
     *             which requires tenantId. Retained for backwards compatibility; defaults to "fan-platform".
     */
    @Deprecated
    public Credential(Long id, String accountId, String email, String credentialHash, String hashAlgorithm,
                      Instant createdAt, Instant updatedAt, int version) {
        this(id, accountId, "fan-platform", email, credentialHash, hashAlgorithm, createdAt, updatedAt, version);
    }

    /**
     * Factory for creating a brand-new credential record (no id yet, version 0).
     * Email is normalized to lower-case + trimmed — the login path performs the
     * same normalization so lookups stay consistent.
     */
    public static Credential create(String accountId, String tenantId, String email,
                                     CredentialHash hash, Instant now) {
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new Credential(
                null,
                accountId,
                Objects.requireNonNull(tenantId, "tenantId must not be null"),
                normalizeEmail(email),
                hash.hash(),
                hash.algorithm(),
                now,
                now,
                0
        );
    }

    /**
     * @deprecated Use {@link #create(String, String, String, CredentialHash, Instant)}.
     *             Retained for backwards compatibility; defaults tenantId to "fan-platform".
     */
    @Deprecated
    public static Credential create(String accountId, String email, CredentialHash hash, Instant now) {
        return create(accountId, "fan-platform", email, hash, now);
    }

    public static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email must not be null");
        return email.trim().toLowerCase();
    }

    /**
     * Return a new {@code Credential} with the given hash applied. The id,
     * accountId, tenantId, email, createdAt are preserved; updatedAt advances to {@code now}
     * and version is incremented by 1 (optimistic-lock contract).
     *
     * <p>Callers must have already validated the new password against
     * {@link PasswordPolicy} before computing {@code newHash}.</p>
     *
     * @param newHash the new credential hash (algorithm + value)
     * @param now     the timestamp to record as updatedAt
     * @return a new Credential instance with the hash replaced
     */
    public Credential changePassword(CredentialHash newHash, Instant now) {
        Objects.requireNonNull(newHash, "newHash must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new Credential(
                this.id,
                this.accountId,
                this.tenantId,
                this.email,
                newHash.hash(),
                newHash.algorithm(),
                this.createdAt,
                now,
                this.version + 1
        );
    }

    public Long getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getCredentialHash() {
        return credentialHash;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
