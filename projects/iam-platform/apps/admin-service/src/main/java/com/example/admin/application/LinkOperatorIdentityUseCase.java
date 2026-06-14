package com.example.admin.application;

import com.example.admin.application.exception.AccountIdentityUnresolvableException;
import com.example.admin.application.exception.IdentityLinkEmailMismatchException;
import com.example.admin.application.exception.OperatorAlreadyLinkedException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.client.AccountServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 (U6 step 3c) — the opt-in, authorized, audited,
 * reversible operator↔central-identity link operation. Backs
 * {@code PATCH /api/admin/operators/{operatorId}/identity:link}.
 *
 * <p>Sets {@code admin_operators.identity_id} to the central identity of an
 * existing consumer account, formalizing the {@code oidc_subject} bridge into a
 * first-class link (U1). It implements the FIXED U3 design and its safety
 * invariants (U7):
 *
 * <ul>
 *   <li><b>opt-in / explicit</b> — only runs on this explicit request; never
 *       automatically and never as a migration backfill.</li>
 *   <li><b>email-match necessary-but-NOT-sufficient</b> — the operator's email
 *       MUST equal the target account's email (necessary). The explicit request
 *       is what authorizes the link; matching email alone never auto-links. A
 *       mismatch is rejected ({@link IdentityLinkEmailMismatchException}). Closes
 *       the § 1.3 cross-tenant email-collision privilege-escalation vector.</li>
 *   <li><b>authorized</b> — gated by {@code operator.manage} scoped to the managed
 *       operator's home tenant (same gate + {@link TenantScopeGuard} call as the
 *       other operator-management mutations).</li>
 *   <li><b>audited</b> — writes an {@code admin_actions} row
 *       ({@link ActionCode#OPERATOR_IDENTITY_LINK}, permission_used=operator.manage)
 *       via {@link AdminActionAuditor#recordWithPermission}.</li>
 *   <li><b>idempotent</b> — re-linking to the SAME identity is a no-op SUCCESS
 *       (still audited). Linking when already linked to a DIFFERENT identity is
 *       rejected ({@link OperatorAlreadyLinkedException}) — caller must unlink
 *       first (U6 reversibility).</li>
 *   <li><b>fail-CLOSED on downstream errors</b> — a {@code DownstreamFailureException}
 *       from {@link AccountServiceClient} (account-service unavailable / errors)
 *       propagates and the link FAILS; a successful resolve that yields a
 *       {@code null} identityId is rejected ({@link AccountIdentityUnresolvableException}).
 *       The link is an authorization decision at link time — the OPPOSITE of the
 *       issuance fail-soft.</li>
 * </ul>
 *
 * <p>Order of checks (cheapest / fail-closed first): load operator (404 if
 * missing) → tenant-scope authorize → resolve account email + central identity
 * (fail-closed) → null-identity reject → email-match necessary → idempotency /
 * already-linked-different → persist link + audit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkOperatorIdentityUseCase {

    private final AdminOperatorPort operatorPort;
    private final AccountServiceClient accountServiceClient;
    private final AdminActionAuditor auditor;
    private final TenantScopeGuard tenantScopeGuard;

    /**
     * @param operatorPublicId target operator's external UUID v7 (path variable)
     * @param accountId        consumer account whose central identity to link
     * @param tenantId         tenant the account lives in (resolve EP path scope)
     * @param actor            authenticated operator (JWT principal)
     * @param reason           caller-typed reason from {@code X-Operator-Reason}
     *
     * @throws OperatorNotFoundException             no operator row for {@code operatorPublicId} (404)
     * @throws AccountIdentityUnresolvableException  account has no central identity (422)
     * @throws IdentityLinkEmailMismatchException    operator/account emails differ (422)
     * @throws OperatorAlreadyLinkedException        already linked to a different identity (409)
     * @throws com.example.admin.application.exception.DownstreamFailureException
     *         account-service unavailable / errors → fail-CLOSED, no link (503)
     */
    @Transactional
    public LinkResult link(String operatorPublicId,
                           String accountId,
                           String tenantId,
                           OperatorContext actor,
                           String reason) {

        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorPublicId));

        // ADR-MONO-024 D2: actor must hold operator.manage scoped to the managed
        // operator's home tenant. Net-zero for SUPER_ADMIN ('*').
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, operator.tenantId(),
                ActionCode.OPERATOR_IDENTITY_LINK);

        // FAIL-CLOSED downstream resolve. Any DownstreamFailureException /
        // NonRetryableDownstreamException from these calls propagates (→ 503/4xx) and
        // the link does NOT happen — this is an authorization decision at link time.
        AccountServiceClient.AccountDetailResponse account =
                accountServiceClient.getDetail(accountId);
        String resolvedIdentityId = accountServiceClient.resolveIdentity(tenantId, accountId);

        // FAIL-CLOSED on a successfully-resolved null identity: nothing to link to.
        if (resolvedIdentityId == null || resolvedIdentityId.isBlank()) {
            throw new AccountIdentityUnresolvableException(
                    "Account " + accountId + " in tenant " + tenantId
                            + " has no resolvable central identity; cannot link operator "
                            + operatorPublicId);
        }

        // Email-match NECESSARY (but the explicit request — not the match — is what
        // authorizes; match alone never auto-links). Case-insensitive compare.
        String operatorEmail = operator.email();
        String accountEmail = account == null ? null : account.email();
        if (operatorEmail == null || accountEmail == null
                || !operatorEmail.equalsIgnoreCase(accountEmail)) {
            throw new IdentityLinkEmailMismatchException(
                    "Operator email does not match account email; link rejected (email-match is a "
                            + "necessary precondition — § 1.3 no-silent-merge)");
        }

        // Idempotency / already-linked checks.
        String currentIdentityId = operator.identityId();
        if (currentIdentityId != null && !currentIdentityId.isBlank()) {
            if (Objects.equals(currentIdentityId, resolvedIdentityId)) {
                // Re-link to the SAME identity → no-op SUCCESS (still audited).
                Instant ts = Instant.now();
                recordAudit(actor, operator, reason, resolvedIdentityId, ts, true);
                return new LinkResult(operatorPublicId, resolvedIdentityId, true);
            }
            throw new OperatorAlreadyLinkedException(
                    "Operator " + operatorPublicId + " is already linked to a different identity; "
                            + "unlink first before re-linking");
        }

        Instant now = Instant.now();
        operatorPort.linkIdentity(operator.internalId(), resolvedIdentityId, now);
        recordAudit(actor, operator, reason, resolvedIdentityId, now, false);
        return new LinkResult(operatorPublicId, resolvedIdentityId, false);
    }

    private void recordAudit(OperatorContext actor,
                             AdminOperatorPort.OperatorView operator,
                             String reason,
                             String identityId,
                             Instant ts,
                             boolean alreadyLinked) {
        // Audit detail records ONLY that the link occurred + the (non-secret)
        // identity_id correlation key + idempotency outcome — not credentials.
        String detail = "identity:" + identityId + (alreadyLinked ? " (idempotent-noop)" : "");
        auditor.recordWithPermission(
                new AdminActionAuditor.AuditRecord(
                        UUID.randomUUID().toString(),
                        ActionCode.OPERATOR_IDENTITY_LINK,
                        actor,
                        "OPERATOR",
                        operator.operatorId(),
                        reason,
                        null,
                        "identity-link:" + operator.operatorId() + ":" + ts.toEpochMilli(),
                        Outcome.SUCCESS,
                        detail,
                        ts,
                        ts,
                        operator.tenantId()),
                Permission.OPERATOR_MANAGE);
    }

    public record LinkResult(
            String operatorId,
            String identityId,
            boolean alreadyLinked
    ) {}
}
