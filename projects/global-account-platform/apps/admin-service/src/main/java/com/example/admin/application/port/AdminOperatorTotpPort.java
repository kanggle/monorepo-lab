package com.example.admin.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-BE-288 — port over {@code admin_operator_totp}. Keeps
 * {@code AdminOperatorTotpJpaEntity} / {@code AdminOperatorTotpJpaRepository}
 * out of the application layer.
 *
 * <p>The recovery-code consume path retains its optimistic-lock retry contract:
 * {@link #tryUpdateRecoveryHashes} returns {@code false} on version conflict so
 * the caller (currently {@code AdminLoginService.consumeRecoveryCode}) can
 * re-read + re-match + re-attempt exactly once, preserving the legacy
 * {@code OptimisticLockException}-driven loop semantics without exposing the
 * JPA exception type to application code.
 */
public interface AdminOperatorTotpPort {

    /** Look up the TOTP row for {@code operatorInternalId}. */
    Optional<TotpRow> findByOperator(long operatorInternalId);

    /**
     * Bulk projection: return the subset of {@code operatorInternalIds} that
     * are TOTP-enrolled (i.e. have an {@code admin_operator_totp} row with
     * a non-null {@code enrolled_at}). Used by the operators listing endpoint
     * to avoid N+1.
     */
    Set<Long> findEnrolledOperatorIds(Collection<Long> operatorInternalIds);

    /**
     * Upsert — create the row if absent, or replace the secret + recovery hash
     * fields on re-enroll. Matches the legacy
     * {@link com.example.admin.application.TotpEnrollmentService}
     * find-or-create branching exactly.
     */
    void upsertSecret(long operatorInternalId,
                      byte[] secretEncrypted,
                      String secretKeyId,
                      String recoveryCodesHashed,
                      Instant enrolledAt);

    /**
     * Stamp {@code last_used_at}. Used by the TOTP verify path (success branch
     * of {@code AdminLoginService} / {@code TotpEnrollmentService.verify}).
     */
    void markUsed(long operatorInternalId, Instant at);

    /**
     * Replace {@code recovery_codes_hashed} and stamp {@code last_used_at}
     * atomically — used by recovery-code regeneration ({@code TASK-BE-113}).
     * No optimistic-lock check: the recovery-code regenerate endpoint is
     * idempotent self-service from the operator's own session and does not
     * race against itself.
     */
    void replaceRecoveryHashes(long operatorInternalId,
                               String recoveryCodesHashed,
                               Instant at);

    /**
     * Version-checked update used by the recovery-code consume path.
     * Returns {@code true} when the update committed (loaded row's
     * {@code version} matched {@code expectedVersion} and JPA flushed without
     * an optimistic-lock failure). Returns {@code false} when either the
     * row's loaded version differs from {@code expectedVersion} or
     * {@code saveAndFlush} surfaces an optimistic-lock exception — the caller
     * must re-read + re-match + retry.
     */
    boolean tryUpdateRecoveryHashes(long operatorInternalId,
                                    int expectedVersion,
                                    String recoveryCodesHashed,
                                    Instant at);

    /**
     * Immutable projection of an {@code admin_operator_totp} row. Exposes
     * {@code version} so the application's recovery-code consume loop can
     * round-trip the JPA optimistic-lock semantics without importing JPA.
     */
    record TotpRow(
            long operatorInternalId,
            byte[] secretEncrypted,
            String secretKeyId,
            String recoveryCodesHashed,
            Instant lastUsedAt,
            Instant enrolledAt,
            int version
    ) {}
}
