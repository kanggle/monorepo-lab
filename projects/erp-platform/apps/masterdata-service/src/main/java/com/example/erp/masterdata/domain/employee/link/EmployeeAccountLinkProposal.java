package com.example.erp.masterdata.domain.employee.link;

import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkConflictException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkNotAddresseeException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkSelfAcceptException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * «Employee E ↔ IAM account A» link proposal (TASK-ERP-BE-044; masterdata-api.md § Employee ↔
 * IAM account link). The write path is <b>HR proposal + account-owner acceptance</b> (owner
 * decision 2026-10-08 UTC, TASK-MONO-774 AC-0).
 *
 * <p><b>The two-person rule lives here, on the accept side</b> — {@link #ensureTwoPersonRule}
 * refuses an acceptor whose {@code sub} equals {@link #proposedBy}. The proposal-time rejection
 * of «propose my own account» in the application service is only an early courtesy: a row that
 * reached the table by any other path (direct DB write, a future bulk tool) must still be
 * unacceptable by its proposer, so the authoritative check is the one that guards
 * {@link #accept} (Failure Scenario 1).
 *
 * <p>Persistence: JPA annotations are the single allowed framework exception in {@code domain/}
 * (architecture.md § Boundary rules); no Spring import. The table's generated column
 * {@code pending_employee_id} (DB-enforced «one PENDING per employee») is deliberately unmapped.
 */
@Entity
@Table(name = "employee_account_link_proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeAccountLinkProposal {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "employee_id", length = 36, nullable = false)
    private String employeeId;

    @Column(name = "account_id", length = 64, nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private LinkProposalStatus status;

    @Column(name = "proposed_by", length = 128, nullable = false)
    private String proposedBy;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 256)
    private String decisionReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * A new {@code PENDING} proposal. Carries no two-person check of its own — see the class
     * javadoc for why the authoritative check sits on {@link #accept}.
     */
    public static EmployeeAccountLinkProposal propose(String id, String tenantId, String employeeId,
                                                      String accountId, String proposedBy,
                                                      String reason, Instant now) {
        EmployeeAccountLinkProposal p = new EmployeeAccountLinkProposal();
        p.id = Objects.requireNonNull(id, "id");
        p.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        p.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        p.accountId = Objects.requireNonNull(accountId, "accountId");
        p.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy");
        p.proposedAt = Objects.requireNonNull(now, "now");
        p.reason = reason;
        p.status = LinkProposalStatus.PENDING;
        return p;
    }

    public boolean isPending() {
        return status == LinkProposalStatus.PENDING;
    }

    /**
     * Contract accept steps 2–4, in contract order, without mutating — so the application can
     * evaluate them BEFORE the employee-side step 5 and still report the contract's first
     * failure. {@link #accept} re-runs the same checks, so no path mutates without them.
     */
    public void ensureAcceptableBy(String callerSub) {
        ensureAddressee(callerSub);
        ensureTwoPersonRule(callerSub);
        ensurePending();
    }

    /** {@code PENDING → ACCEPTED}; {@code decidedBy} = the acceptor. */
    public void accept(String callerSub, Instant now) {
        ensureAcceptableBy(callerSub);
        this.status = LinkProposalStatus.ACCEPTED;
        this.decidedBy = callerSub;
        this.decidedAt = now;
    }

    /** {@code PENDING → DECLINED} — account owner only. */
    public void decline(String callerSub, String declineReason, Instant now) {
        ensureAddressee(callerSub);
        ensurePending();
        this.status = LinkProposalStatus.DECLINED;
        this.decidedBy = callerSub;
        this.decidedAt = now;
        this.decisionReason = declineReason;
    }

    /** {@code PENDING → REVOKED} — the caller's authority (erp.write + scope) is checked upstream. */
    public void revoke(String actorId, String revokeReason, Instant now) {
        ensurePending();
        this.status = LinkProposalStatus.REVOKED;
        this.decidedBy = actorId;
        this.decidedAt = now;
        this.decisionReason = revokeReason;
    }

    private void ensureAddressee(String callerSub) {
        if (callerSub == null || !callerSub.equals(accountId)) {
            throw new EmployeeLinkNotAddresseeException(
                    "Only the proposed account may accept or decline proposal " + id);
        }
    }

    /**
     * 🔴 Two-person rule (masterdata-api.md accept step 3). The proposer and the acceptor must be
     * different people — otherwise an HR-privileged account proposes itself for any employee
     * (e.g. the CFO) and accepts its own proposal, taking over that employee's approval inbox.
     */
    private void ensureTwoPersonRule(String callerSub) {
        if (callerSub != null && callerSub.equals(proposedBy)) {
            throw new EmployeeLinkSelfAcceptException(
                    "Two-person rule: the proposer of " + id + " cannot accept it");
        }
    }

    private void ensurePending() {
        if (!isPending()) {
            throw new EmployeeLinkConflictException(EmployeeLinkConflictException.PROPOSAL_NOT_PENDING,
                    "Proposal " + id + " is " + status + ", not PENDING");
        }
    }
}
