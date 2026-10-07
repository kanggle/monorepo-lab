package com.example.erp.masterdata.domain.employee.link;

import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkConflictException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkNotAddresseeException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkSelfAcceptException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests for {@link EmployeeAccountLinkProposal} (TASK-ERP-BE-044) — no Spring.
 * The two-person rule tests here are the AC-2 bite targets: they construct a proposal whose
 * proposer IS the addressee directly through the factory — i.e. a row that bypassed the
 * application's proposal-time check — and require the ACCEPT side to refuse it.
 */
class EmployeeAccountLinkProposalTest {

    private static final Instant T0 = Instant.parse("2026-10-08T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-08T01:00:00Z");

    private static EmployeeAccountLinkProposal pending(String accountId, String proposedBy) {
        return EmployeeAccountLinkProposal.propose("p-1", "erp", "emp-1", accountId, proposedBy,
                "onboarding", T0);
    }

    @Test
    @DisplayName("propose → PENDING with proposer, time and reason; no decision yet")
    void proposeIsPending() {
        EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");

        assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.PENDING);
        assertThat(p.isPending()).isTrue();
        assertThat(p.getProposedBy()).isEqualTo("hr-1");
        assertThat(p.getProposedAt()).isEqualTo(T0);
        assertThat(p.getReason()).isEqualTo("onboarding");
        assertThat(p.getDecidedBy()).isNull();
        assertThat(p.getDecidedAt()).isNull();
    }

    @Test
    @DisplayName("accept by the addressee (≠ proposer) → ACCEPTED, decidedBy = acceptor")
    void acceptByAddressee() {
        EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");

        p.accept("acct-A", T1);

        assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.ACCEPTED);
        assertThat(p.getDecidedBy()).isEqualTo("acct-A");
        assertThat(p.getDecidedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("🔴 AC-2 two-person rule: the proposer cannot accept its own proposal → SELF_ACCEPT, state unchanged")
    void proposerCannotAccept() {
        // A self-addressed row: proposer == account. The application refuses to CREATE one, so
        // this simulates a row that arrived by another path (direct DB / future bulk tool).
        EmployeeAccountLinkProposal p = pending("acct-A", "acct-A");

        assertThatThrownBy(() -> p.accept("acct-A", T1))
                .isInstanceOf(EmployeeLinkSelfAcceptException.class);
        assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.PENDING);
        assertThat(p.getDecidedBy()).isNull();
    }

    @Test
    @DisplayName("🔴 AC-2 two-person rule also on the non-mutating pre-check")
    void proposerFailsPreCheck() {
        EmployeeAccountLinkProposal p = pending("acct-A", "acct-A");

        assertThatThrownBy(() -> p.ensureAcceptableBy("acct-A"))
                .isInstanceOf(EmployeeLinkSelfAcceptException.class);
    }

    @Test
    @DisplayName("AC-3 accept by someone other than the addressee → NOT_ADDRESSEE (checked before the two-person rule)")
    void acceptByNonAddressee() {
        EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");

        // hr-1 is the proposer AND not the addressee: contract step 2 (addressee) precedes step 3.
        assertThatThrownBy(() -> p.accept("hr-1", T1))
                .isInstanceOf(EmployeeLinkNotAddresseeException.class);
        assertThatThrownBy(() -> p.accept("someone-else", T1))
                .isInstanceOf(EmployeeLinkNotAddresseeException.class);
        assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.PENDING);
    }

    @Test
    @DisplayName("decline: addressee only; PENDING → DECLINED with reason")
    void decline() {
        EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");

        assertThatThrownBy(() -> p.decline("hr-1", "no", T1))
                .isInstanceOf(EmployeeLinkNotAddresseeException.class);

        p.decline("acct-A", "not me", T1);
        assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.DECLINED);
        assertThat(p.getDecidedBy()).isEqualTo("acct-A");
        assertThat(p.getDecisionReason()).isEqualTo("not me");
    }

    @Test
    @DisplayName("revoke: PENDING → REVOKED, decidedBy = revoker (need not be the proposer)")
    void revoke() {
        EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");

        p.revoke("hr-2", "wrong person", T1);

        assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.REVOKED);
        assertThat(p.getDecidedBy()).isEqualTo("hr-2");
        assertThat(p.getDecisionReason()).isEqualTo("wrong person");
    }

    @Test
    @DisplayName("non-PENDING rows are immutable: every transition → CONFLICT proposal_not_pending")
    void decidedRowIsImmutable() {
        EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");
        p.revoke("hr-1", "x", T1);

        assertThatThrownBy(() -> p.accept("acct-A", T1))
                .isInstanceOfSatisfying(EmployeeLinkConflictException.class, e ->
                        assertThat(e.conflictCause()).isEqualTo("proposal_not_pending"));
        assertThatThrownBy(() -> p.decline("acct-A", null, T1))
                .isInstanceOf(EmployeeLinkConflictException.class);
        assertThatThrownBy(() -> p.revoke("hr-1", "again", T1))
                .isInstanceOf(EmployeeLinkConflictException.class);
        assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.REVOKED);
        assertThat(p.getDecidedBy()).isEqualTo("hr-1");
    }
}
