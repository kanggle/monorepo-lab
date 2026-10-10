package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorInvitationAlreadyPendingException;
import com.example.admin.application.exception.OperatorInvitationNotPendingException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.common.id.UuidV7;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-772 S2 — the transactional half of {@link OperatorInvitationUseCase}: each method is ONE admin_db
 * transaction that writes the invitation row and its {@code admin_actions} row together (audit-heavy A3 — no write
 * without its audit row, no audit row for a write that rolled back).
 *
 * <p>Why a separate bean: the mail must leave <b>after commit</b> (admin-api.md step 10 — a rolled-back invitation
 * must not leave a live link in someone's inbox). The use case calls this bean (the cross-bean call is what lets
 * Spring open and commit the transaction), and only once it has returned does the use case call the mail port.
 *
 * <p>The raw token is minted here and returned to the caller in memory only ({@link Issued#token}); the row gets
 * its SHA-256 hex.
 */
@Component
public class OperatorInvitationWriter {

    static final String TARGET_TYPE = "OPERATOR_INVITATION";

    private final OperatorInvitationPort invitationPort;
    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;
    private final Duration ttl;

    public OperatorInvitationWriter(OperatorInvitationPort invitationPort,
                                    AdminOperatorPort operatorPort,
                                    AdminActionAuditor auditor,
                                    @Value("${admin.operator-invitation.ttl:P7D}") String ttl) {
        this.invitationPort = invitationPort;
        this.operatorPort = operatorPort;
        this.auditor = auditor;
        this.ttl = Duration.parse(ttl);
        if (this.ttl.isNegative() || this.ttl.isZero()) {
            throw new IllegalStateException("admin.operator-invitation.ttl must be positive: " + ttl);
        }
    }

    /**
     * The invitation as it now stands, plus the raw token that the caller must hand to the mail port and then
     * drop. 🔴 {@code toString} redacts the token.
     */
    public record Issued(InvitationView invitation, String token, String auditId) {
        @Override
        public String toString() {
            return "Issued[invitation=" + invitation.invitationId() + ", token=<redacted>, auditId=" + auditId + "]";
        }
    }

    Duration ttl() {
        return ttl;
    }

    /** Steps 7–9 of the create: conflicts → the row → the audit row, one transaction. */
    @Transactional
    public Issued createPending(OperatorContext actor, String tenantId, String email, String displayName,
                                List<String> roleNames, String idempotencyKey, String reason) {
        if (operatorPort.existsByTenantIdAndEmail(tenantId, email)) {
            throw new OperatorEmailConflictException("An operator with this email already exists in the tenant");
        }
        if (invitationPort.existsPending(tenantId, email)) {
            throw new OperatorInvitationAlreadyPendingException(
                    "A pending invitation for this email already exists in the tenant — resend it instead");
        }
        long actorInternalId = actorInternalId(actor);
        Instant now = Instant.now();
        String token = OperatorInvitationTokens.newToken();
        InvitationView created = invitationPort.create(new OperatorInvitationPort.NewInvitation(
                UuidV7.randomString(), tenantId, email, displayName, roleNames,
                OperatorInvitationTokens.sha256Hex(token), now.plus(ttl), actorInternalId, now));

        String auditId = auditor.newAuditId();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId, ActionCode.OPERATOR_INVITATION_CREATE, actor,
                TARGET_TYPE, created.invitationId(),
                AuditReasons.normalize(reason), null,
                idempotencyKey,  // the client key — what isIdempotencyKeyUsed looks up on the next attempt
                Outcome.SUCCESS, "roles=" + String.join(",", created.roles()),
                now, Instant.now(), tenantId));
        return new Issued(created, token, auditId);
    }

    /**
     * {@code PENDING → CANCELLED} + audit. A lost race to a concurrent cancel is the idempotent no-op it would
     * have been anyway; a lost race to an accept is 409.
     *
     * @return {@code true} when this call made the transition (an audit row was written)
     */
    @Transactional
    public boolean cancel(OperatorContext actor, InvitationView invitation, String reason) {
        long actorInternalId = actorInternalId(actor);
        Instant now = Instant.now();
        if (!invitationPort.cancelIfPending(invitation.internalId(), actorInternalId, now)) {
            String current = invitationPort.findByInvitationId(invitation.invitationId())
                    .map(InvitationView::status).orElse(null);
            if ("CANCELLED".equals(current)) {
                return false;
            }
            throw new OperatorInvitationNotPendingException(
                    "The invitation is no longer pending (accepted) — it cannot be cancelled");
        }
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditor.newAuditId(), ActionCode.OPERATOR_INVITATION_CANCEL, actor,
                TARGET_TYPE, invitation.invitationId(),
                AuditReasons.normalize(reason), null,
                "invitation-cancel:" + invitation.invitationId(),
                Outcome.SUCCESS, null, now, Instant.now(), invitation.tenantId()));
        return true;
    }

    /**
     * Resend: a new token on the SAME row (the old hash is overwritten — the old link dies), a new expiry, the
     * re-sender as {@code invited_by}; guarded by status and the version that was read.
     */
    @Transactional
    public Issued rotate(OperatorContext actor, InvitationView invitation, String reason) {
        long actorInternalId = actorInternalId(actor);
        Instant now = Instant.now();
        String token = OperatorInvitationTokens.newToken();
        boolean rotated = invitationPort.rotateIfPending(invitation.internalId(), invitation.version(),
                OperatorInvitationTokens.sha256Hex(token), now.plus(ttl), actorInternalId, now);
        if (!rotated) {
            throw new OperatorInvitationNotPendingException(
                    "The invitation changed meanwhile (accepted, cancelled or resent) — reload and retry");
        }
        String auditId = auditor.newAuditId();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId, ActionCode.OPERATOR_INVITATION_RESEND, actor,
                TARGET_TYPE, invitation.invitationId(),
                AuditReasons.normalize(reason), null,
                "invitation-resend:" + auditId,
                Outcome.SUCCESS, null, now, Instant.now(), invitation.tenantId()));
        InvitationView after = invitationPort.findByInvitationId(invitation.invitationId())
                .orElseThrow(() -> new IllegalStateException("rotated invitation vanished"));
        return new Issued(after, token, auditId);
    }

    private long actorInternalId(OperatorContext actor) {
        return operatorPort.findByOperatorId(actor.operatorId())
                .map(AdminOperatorPort.OperatorView::internalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + actor.operatorId()));
    }
}
