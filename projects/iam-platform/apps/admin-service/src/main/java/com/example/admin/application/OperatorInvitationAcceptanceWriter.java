package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.common.id.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * TASK-MONO-772 S3 (auth-to-admin.md § accept, step 8) — the ONE admin_db transaction of an acceptance:
 *
 * <ol>
 *   <li>the claim — {@code PENDING → ACCEPTED} guarded by status, the presented token's hash and «not expired»
 *       (a conditional UPDATE; 0 rows ⇒ {@link ClaimLost}, nothing else is written);</li>
 *   <li>the operator row — home = the invitation's tenant, {@code password_hash = NULL} (OIDC only),
 *       🔴 {@code oidc_subject = accountId} (AC-0 F1: the login door — the token exchange finds the operator by
 *       it), {@code ACTIVE};</li>
 *   <li>the role grants — each invited role, grant tenant = the invitation's tenant (ADR-MONO-024 D2);</li>
 *   <li>the whole-tenant assignment;</li>
 *   <li>{@code accepted_operator_id} on the invitation;</li>
 *   <li>the {@code OPERATOR_INVITATION_ACCEPT} SUCCESS audit row (+ outbox), actor = the operator just created.</li>
 * </ol>
 *
 * <p>Any failure rolls back all six (audit-heavy A3: no write without its audit row, no audit row for a write that
 * rolled back). A separate bean so the use case's call crosses the proxy (the transaction opens and commits here),
 * and so the use case can re-read <b>after</b> a lost claim in a fresh read — inside this transaction a
 * REPEATABLE READ snapshot could still show the row as it was before the winner committed.
 */
@Component
@RequiredArgsConstructor
public class OperatorInvitationAcceptanceWriter {

    static final String STATUS_ACTIVE = "ACTIVE";

    private final OperatorInvitationPort invitationPort;
    private final AdminOperatorPort operatorPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final AdminActionAuditor auditor;
    /** TASK-MONO-784 S5 — the shared «add a membership to an existing row» writes (D-12). */
    private final OperatorMembershipAddition membershipAddition;
    /**
     * TASK-MONO-773 S4 (OD-5) — a first-admin invitation's acceptance also writes the founding admin's ORIGIN set
     * row, in the same transaction (auth-to-admin.md § accept «첫 관리자 초대일 때» 8). The S3 code path.
     */
    private final OriginSetWriter originSetWriter;

    /** The claim lost: a concurrent accept / cancel / resend won, or the invitation expired meanwhile. */
    public static final class ClaimLost extends RuntimeException {
        ClaimLost() {
            super("operator invitation is no longer claimable");
        }
    }

    /** The operator this acceptance created. */
    public record Accepted(String operatorId, long operatorInternalId, String auditId) {}

    /**
     * @param invitation        the invitation as read (its {@code tokenHash} is the presented token's hash)
     * @param accountId         the accepting pool account (the IdP session's — never a form value)
     * @param roles             the invitation's roles, resolved and re-judged against the inviter's menu (step 5)
     * @param inviterInternalId {@code granted_by} of the grants and the assignment — the operator whose live token
     *                          this is (the inviter or the last re-sender, S1-9)
     */
    @Transactional
    public Accepted accept(InvitationView invitation, String accountId,
                           Collection<AdminOperatorPort.RoleView> roles, long inviterInternalId) {
        Instant now = Instant.now();
        String tenantId = invitation.tenantId();

        // 1. the claim — the affected-row count IS the decision (0 = someone else's write already holds the row).
        if (!invitationPort.acceptIfPending(invitation.internalId(), invitation.tokenHash(), accountId, now)) {
            throw new ClaimLost();
        }

        // 2. the operator row (the (tenant_id, email) collision surfaces as OperatorEmailConflictException).
        String operatorUuid = UuidV7.randomString();
        AdminOperatorPort.OperatorView created = operatorPort.createOperator(new AdminOperatorPort.NewOperator(
                operatorUuid, tenantId, invitation.email(), null, invitation.displayName(), STATUS_ACTIVE, now));
        // 🔴 the login door (F1). The UNIQUE on oidc_subject is the DB's half of OD-1 (step 6 is the pre-check).
        operatorPort.updateOidcSubject(created.internalId(), accountId, now);

        // 3. grants — confined to the invitation's tenant (D2), never the inviter's, never '*'.
        List<AdminOperatorPort.NewRoleBinding> bindings = roles.stream()
                .map(role -> new AdminOperatorPort.NewRoleBinding(
                        created.internalId(), role.id(), now, inviterInternalId, tenantId))
                .toList();
        operatorPort.saveOperatorRoles(bindings);

        // 4. whole-tenant assignment (org_scope NULL ⟺ ["*"]) — the home-tenant operator's existing shape.
        assignmentPort.createAssignment(created.internalId(), tenantId, inviterInternalId);

        // 5. which operator the invitation became.
        invitationPort.recordAcceptedOperator(invitation.internalId(), created.internalId(), now);

        // 5b. TASK-MONO-773 OD-5 — the founding admin's ORIGIN set (first-admin invitations only).
        if (invitation.firstAdmin()) {
            originSetWriter.write(created.internalId(), tenantId, now);
        }

        // 6. the audit row. Neither the token nor the email rides in it; accountId does (F19 — 774's link source).
        String auditId = auditor.newAuditId();
        auditor.recordWithPermission(new AdminActionAuditor.AuditRecord(
                auditId, ActionCode.OPERATOR_INVITATION_ACCEPT, new OperatorContext(operatorUuid, null),
                OperatorInvitationWriter.TARGET_TYPE, invitation.invitationId(),
                AdminActionAuditor.REASON_SELF_INVITATION_ACCEPT, null,
                "invitation-accept:" + invitation.invitationId(),
                Outcome.SUCCESS, "accountId=" + accountId,
                now, Instant.now(), tenantId),
                AdminActionAuditor.PERMISSION_SELF_INVITATION_ACCEPT);

        return new Accepted(operatorUuid, created.internalId(), auditId);
    }

    /**
     * TASK-MONO-784 S5 (auth-to-admin.md § accept «소속 추가일 때» — step 6 ② · ADR-MONO-082 b · D-12) — the
     * accepting account already has an ACTIVE company operator row that is not yet a member of the invitation's
     * tenant (the use case judged it with {@link OperatorMembershipAddition#requireCanReceive}). ONE transaction:
     *
     * <ol>
     *   <li>the same claim as {@link #accept} ({@link ClaimLost} on 0 rows);</li>
     *   <li>🔴 NO operator-row write — home · {@code oidc_subject} · {@code email} · {@code display_name} ·
     *       {@code status} stay; the invitation's {@code displayName} is not used (S1-7);</li>
     *   <li>the membership — the invited roles as direct rows with {@code tenant_id} = the invitation's tenant +
     *       the whole-tenant assignment ({@link OperatorMembershipAddition#write}); the rows of the operator's other
     *       companies are not touched (784 AC-3 byte-unchanged);</li>
     *   <li>{@code accepted_operator_id} = the EXISTING operator;</li>
     *   <li>the audit row as for a new row — actor = the existing operator, {@code downstream_detail} carries
     *       {@code membershipAdded=true} after {@code accountId=…}.</li>
     * </ol>
     */
    @Transactional
    public Accepted acceptAsMembership(InvitationView invitation, String accountId,
                                       AdminOperatorPort.OperatorView existing,
                                       Collection<AdminOperatorPort.RoleView> roles, long inviterInternalId) {
        Instant now = Instant.now();
        String tenantId = invitation.tenantId();

        if (!invitationPort.acceptIfPending(invitation.internalId(), invitation.tokenHash(), accountId, now)) {
            throw new ClaimLost();
        }

        membershipAddition.write(existing.internalId(), tenantId, roles, inviterInternalId, now);

        invitationPort.recordAcceptedOperator(invitation.internalId(), existing.internalId(), now);

        // TASK-MONO-773 OD-5 — a first-admin invitation: the ORIGIN set in THIS tenant only (the other companies'
        // set rows are not read or written).
        if (invitation.firstAdmin()) {
            originSetWriter.write(existing.internalId(), tenantId, now);
        }

        String auditId = auditor.newAuditId();
        auditor.recordWithPermission(new AdminActionAuditor.AuditRecord(
                auditId, ActionCode.OPERATOR_INVITATION_ACCEPT, new OperatorContext(existing.operatorId(), null),
                OperatorInvitationWriter.TARGET_TYPE, invitation.invitationId(),
                AdminActionAuditor.REASON_SELF_INVITATION_ACCEPT, null,
                "invitation-accept:" + invitation.invitationId(),
                Outcome.SUCCESS, "accountId=" + accountId + ",membershipAdded=true",
                now, Instant.now(), tenantId),
                AdminActionAuditor.PERMISSION_SELF_INVITATION_ACCEPT);

        return new Accepted(existing.operatorId(), existing.internalId(), auditId);
    }
}
