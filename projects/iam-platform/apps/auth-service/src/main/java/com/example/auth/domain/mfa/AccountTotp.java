package com.example.auth.domain.mfa;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TASK-MONO-771 (ADR-MONO-080 D4 · R3) — one account's authenticator-app enrollment ({@code account_totp},
 * data-model.md § account_totp). Pure domain object: no framework type, no cryptography — the secret is held
 * only as the ciphertext the infrastructure produced, and codes are computed outside.
 *
 * <p>Two states:
 * <ul>
 *   <li><b>pending</b> ({@code confirmedAt == null}) — written by {@code GET /mfa/setup}, lives
 *       {@link #PENDING_LIFETIME}. It passes no check: the login second step and step-up read confirmed rows
 *       only ({@link #isConfirmed()}).</li>
 *   <li><b>confirmed</b> — the first code from the app was accepted; carries the recovery-code hashes and the
 *       anti-replay counter.</li>
 * </ul>
 *
 * <p>🔴 <b>Replay guard</b> (AC-0 F5): {@link #isStepFresh(long)} is the single place that refuses a time-step
 * at or below {@link #getLastUsedStep()}. The admin-service break-glass verifier has no such guard; this
 * account-plane one must.
 */
public class AccountTotp {

    /** How long a pending (unconfirmed) secret may be confirmed (auth-api.md § /mfa/setup). */
    public static final Duration PENDING_LIFETIME = Duration.ofMinutes(10);

    private final String accountId;
    private String tenantId;
    private byte[] secretCiphertext;
    private String secretKeyId;
    private Instant confirmedAt;
    private List<String> recoveryCodeHashes;
    private Long lastUsedStep;
    private Instant lastUsedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Integer version;

    public AccountTotp(String accountId, String tenantId, byte[] secretCiphertext, String secretKeyId,
                       Instant confirmedAt, List<String> recoveryCodeHashes, Long lastUsedStep,
                       Instant lastUsedAt, Instant createdAt, Instant updatedAt, Integer version) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.secretCiphertext = Objects.requireNonNull(secretCiphertext, "secretCiphertext").clone();
        this.secretKeyId = Objects.requireNonNull(secretKeyId, "secretKeyId");
        this.confirmedAt = confirmedAt;
        this.recoveryCodeHashes = recoveryCodeHashes == null ? null : new ArrayList<>(recoveryCodeHashes);
        this.lastUsedStep = lastUsedStep;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /** A fresh pending enrollment (replaces any earlier pending one — never a confirmed one). */
    public static AccountTotp pending(String accountId, String tenantId, byte[] secretCiphertext,
                                      String secretKeyId, Instant now) {
        return new AccountTotp(accountId, tenantId, secretCiphertext, secretKeyId,
                null, null, null, null, now, now, null);
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    /** A pending row older than {@link #PENDING_LIFETIME} can no longer be confirmed. */
    public boolean isPendingExpired(Instant now) {
        return !isConfirmed() && createdAt.plus(PENDING_LIFETIME).isBefore(now);
    }

    /**
     * 🔴 The anti-replay predicate: a time-step is acceptable only if it is strictly later than the last one
     * this enrollment accepted. Inside the ±1 window an attacker who saw a code could otherwise replay it for
     * up to 90 seconds.
     */
    public boolean isStepFresh(long timeStep) {
        return lastUsedStep == null || timeStep > lastUsedStep;
    }

    /** The first app code was accepted: pending → confirmed, with the recovery codes and the step consumed. */
    public void confirm(long acceptedStep, List<String> recoveryCodeHashes, Instant now) {
        if (isConfirmed()) {
            throw new IllegalStateException("enrollment is already confirmed");
        }
        this.confirmedAt = now;
        this.lastUsedStep = acceptedStep;
        this.lastUsedAt = now;
        this.recoveryCodeHashes = new ArrayList<>(recoveryCodeHashes);
        this.updatedAt = now;
    }

    /** An app code of {@code acceptedStep} passed the second step — the counter moves forward. */
    public void recordAuthenticatorSuccess(long acceptedStep, Instant now) {
        requireConfirmed();
        this.lastUsedStep = acceptedStep;
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    /** The recovery code whose hash sits at {@code index} was used — it is gone (single use). */
    public void consumeRecoveryCode(int index, Instant now) {
        requireConfirmed();
        if (recoveryCodeHashes == null || index < 0 || index >= recoveryCodeHashes.size()) {
            throw new IllegalArgumentException("no recovery code at index " + index);
        }
        recoveryCodeHashes.remove(index);
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    /** A new set of recovery codes replaces ALL previous ones (they stop working at once). */
    public void replaceRecoveryCodes(List<String> hashes, Instant now) {
        requireConfirmed();
        this.recoveryCodeHashes = new ArrayList<>(hashes);
        this.updatedAt = now;
    }

    /** Lazy re-encryption under the active key (security.md § Rotation Procedure, admin's procedure). */
    public void reencrypt(byte[] ciphertext, String keyId, Instant now) {
        this.secretCiphertext = Objects.requireNonNull(ciphertext, "ciphertext").clone();
        this.secretKeyId = Objects.requireNonNull(keyId, "keyId");
        this.updatedAt = now;
    }

    public int remainingRecoveryCodes() {
        return recoveryCodeHashes == null ? 0 : recoveryCodeHashes.size();
    }

    private void requireConfirmed() {
        if (!isConfirmed()) {
            throw new IllegalStateException("enrollment is not confirmed");
        }
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public byte[] getSecretCiphertext() {
        return secretCiphertext.clone();
    }

    public String getSecretKeyId() {
        return secretKeyId;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    /** A copy — callers cannot edit the stored hashes. Empty for a pending row. */
    public List<String> getRecoveryCodeHashes() {
        return recoveryCodeHashes == null ? List.of() : List.copyOf(recoveryCodeHashes);
    }

    /** {@code true} when the row stores no recovery-code array at all (pending — the column is NULL). */
    public boolean hasNoRecoveryCodeColumn() {
        return recoveryCodeHashes == null;
    }

    public Long getLastUsedStep() {
        return lastUsedStep;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getVersion() {
        return version;
    }
}
