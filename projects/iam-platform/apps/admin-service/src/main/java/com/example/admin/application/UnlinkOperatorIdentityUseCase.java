package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 (U6 step 3c) — the reversal half of the opt-in
 * operator↔identity link: clears {@code admin_operators.identity_id}. Backs
 * {@code PATCH /api/admin/operators/{operatorId}/identity:unlink}.
 *
 * <p>U6 reversibility invariant — the link is reversible until ADR-032 step 4
 * begins the credential consolidation. Authorized by {@code operator.manage}
 * scoped to the operator's home tenant (same gate as link) and audited
 * ({@link ActionCode#OPERATOR_IDENTITY_UNLINK}).
 *
 * <p>Idempotent: unlinking an already-unlinked operator is a no-op SUCCESS (still
 * audited). No downstream call is needed — unlink only clears a local column.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnlinkOperatorIdentityUseCase {

    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;
    private final TenantScopeGuard tenantScopeGuard;

    /**
     * @param operatorPublicId target operator's external UUID v7 (path variable)
     * @param actor            authenticated operator (JWT principal)
     * @param reason           caller-typed reason from {@code X-Operator-Reason}
     *
     * @throws OperatorNotFoundException no operator row for {@code operatorPublicId} (404)
     */
    @Transactional
    public UnlinkResult unlink(String operatorPublicId,
                               OperatorContext actor,
                               String reason) {

        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorPublicId));

        tenantScopeGuard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, operator.tenantId(),
                ActionCode.OPERATOR_IDENTITY_UNLINK);

        String previousIdentityId = operator.identityId();
        boolean alreadyUnlinked = (previousIdentityId == null || previousIdentityId.isBlank());

        Instant now = Instant.now();
        if (!alreadyUnlinked) {
            operatorPort.unlinkIdentity(operator.internalId(), now);
        }

        String detail = alreadyUnlinked
                ? "identity:<none> (idempotent-noop)"
                : "identity:" + previousIdentityId + " -> <cleared>";
        auditor.recordWithPermission(
                new AdminActionAuditor.AuditRecord(
                        UUID.randomUUID().toString(),
                        ActionCode.OPERATOR_IDENTITY_UNLINK,
                        actor,
                        "OPERATOR",
                        operator.operatorId(),
                        reason,
                        null,
                        "identity-unlink:" + operator.operatorId() + ":" + now.toEpochMilli(),
                        Outcome.SUCCESS,
                        detail,
                        now,
                        now,
                        operator.tenantId()),
                Permission.OPERATOR_MANAGE);

        return new UnlinkResult(operatorPublicId, previousIdentityId, alreadyUnlinked);
    }

    public record UnlinkResult(
            String operatorId,
            String previousIdentityId,
            boolean alreadyUnlinked
    ) {}
}
