package com.example.admin.application.port;

/**
 * TASK-MONO-773 S3 (owner decision OD-3 — onboarding-api.md § 신원당 상한 «경합») — a pessimistic row lock on one
 * {@code admin_operators} row ({@code SELECT … FOR UPDATE}), held until the CALLER's transaction ends.
 *
 * <p>The self-service «second company» re-counts the per-identity cap under this lock, so two concurrent creations
 * by the same operator row queue on it and the one that would exceed the cap is refused (TASK-MONO-784 S5 recorded
 * the cap + 1 race this closes).
 *
 * <p>🔴 Call it as the FIRST statement of the transaction: under InnoDB REPEATABLE READ the consistent-read snapshot
 * is taken at the transaction's first plain read, so a read before the lock would freeze a view from before the
 * competing transaction committed, and the re-count would miss its rows.
 */
public interface OperatorRowLockPort {

    /**
     * Locks the row; joins the caller's transaction (there must be one).
     *
     * @throws com.example.admin.application.exception.OperatorNotFoundException the row does not exist
     */
    void lockForUpdate(long operatorInternalId);
}
