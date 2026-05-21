package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.port.AdminOperatorPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * TASK-BE-306 — self-serve operator profile mutation use case (write-path
 * counterpart of TASK-BE-304 read-path). Backs
 * {@code PATCH /api/admin/operators/me/profile}.
 *
 * <p>v1 surface: mutates {@code admin_operators.finance_default_account_id}
 * of the calling operator's own row. {@code defaultAccountId == null} clears
 * the column; a non-null value is treated as an opaque finance-platform
 * account UUID (GAP does NOT verify against finance-platform — see
 * {@code admin-api.md § PATCH /api/admin/operators/me/profile} + TASK-BE-304
 * § Decision authority "Why {@code validation = opaque on producer}").
 *
 * <p>Single transaction wraps both writes (column UPDATE + audit row INSERT):
 * audit-heavy A3 invariant means a row mutation without an audit trail is
 * forbidden. If the audit write fails the column UPDATE rolls back, and
 * vice versa.
 *
 * <p>Structural validation (length, whitespace, control chars) lives on the
 * presentation DTO {@code UpdateOperatorProfileRequest} so a malformed body
 * is rejected before this use case is entered.
 */
@Service
@RequiredArgsConstructor
public class UpdateOwnOperatorProfileUseCase {

    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;

    /**
     * Sets or clears the calling operator's
     * {@code finance_default_account_id} column and writes the corresponding
     * audit row in the same transaction.
     *
     * @param caller             the authenticated operator (used for both the
     *                           target row and the audit operator_id);
     *                           {@code OperatorContext.operatorId()} is the
     *                           external UUID v7 (JWT {@code sub})
     * @param defaultAccountId   the new value (opaque UUID string), or
     *                           {@code null} to clear; structural validation
     *                           is the caller's responsibility
     *
     * @throws OperatorUnauthorizedException when the operator row cannot be
     *         resolved by JWT {@code sub} (soft-deleted between JWT verify
     *         and use-case entry, or stale token); mapped to
     *         {@code 401 TOKEN_INVALID} by {@code AdminExceptionHandler}.
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException
     *         on {@code admin_operators.version} race (two browser tabs); the
     *         framework surfaces this via {@code @Version}; mapped to
     *         {@code 409 OPTIMISTIC_LOCK_CONFLICT}.
     */
    @Transactional
    public void update(OperatorContext caller, String defaultAccountId) {
        AdminOperatorPort.OperatorView operator = operatorPort
                .findByOperatorId(caller.operatorId())
                .orElseThrow(() -> new OperatorUnauthorizedException(
                        "Operator not found for operatorId=" + caller.operatorId()));

        Instant now = Instant.now();
        operatorPort.changeFinanceDefaultAccountId(operator.internalId(), defaultAccountId, now);

        // Audit row written in the SAME transaction. detail IS NULL by design —
        // the audit subject is *that the value changed*, not the value itself
        // (R4/A3 invariant; the new value lives in the operator row and is
        // observable via the next GET /api/admin/console/registry round-trip).
        auditor.record(new AdminActionAuditor.AuditRecord(
                UUID.randomUUID().toString(),
                ActionCode.OPERATOR_PROFILE_UPDATE,
                caller,
                "OPERATOR",
                caller.operatorId(),
                AdminActionAuditor.REASON_SELF_PROFILE_UPDATE,
                null,
                "self-profile-update:" + caller.operatorId() + ":" + now.toEpochMilli(),
                Outcome.SUCCESS,
                null,
                now,
                now,
                operator.tenantId()));
    }
}
