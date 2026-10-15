package com.example.erp.approval.infrastructure.authorization;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.domain.authorization.AuthorizationDecision.Outcome;
import com.example.erp.approval.domain.authorization.RequiredScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions, 2026-10-15 UTC) — the approval business-role verdict
 * (erp iam-integration.md § 업무 역할): READ = {@code ERP_APPROVAL_READ ∨ ERP_APPROVAL_WRITE ∨ erp.read ∨
 * erp.write}, WRITE = {@code ERP_APPROVAL_WRITE ∨ erp.write}. Both halves are asserted — the {@link ActorContext}
 * short-circuit the application service uses ({@code canReadErp} / {@code canWriteErp}) AND the port behind it
 * ({@link JwtBackedAuthorizationAdapter}) — because the service consults the first and falls back to the second;
 * a role left in either would reopen the path.
 *
 * <p>Every «set» token carries the derived entry role {@code ERP_OPERATOR}, as auth-service mints it.
 * Approver eligibility (separation of duties) is the aggregate's guard and does not read roles at all —
 * {@code ApprovalRouteTest} / {@code ApprovalApplicationServiceTest} keep pinning it unchanged.
 */
class ApprovalBusinessRoleGateTest {

    private final JwtBackedAuthorizationAdapter adapter = new JwtBackedAuthorizationAdapter();

    static final Set<String> LEGACY = Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE",
            "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE");
    static final Set<String> MASTERDATA_SET = Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE");
    static final Set<String> APPROVAL_READ_SET = Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ");
    static final Set<String> APPROVAL_SET = Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE");

    private static ActorContext actor(Set<String> roles, Set<String> entitled) {
        return new ActorContext("acct-1", "demo-corp", roles, Set.of("*"), entitled);
    }

    private void expect(Set<String> roles, Set<String> entitled, boolean read, boolean write) {
        ActorContext a = actor(roles, entitled);
        assertThat(a.canReadErp()).as("canReadErp %s", roles).isEqualTo(read);
        assertThat(a.canWriteErp()).as("canWriteErp %s", roles).isEqualTo(write);
        assertThat(adapter.evaluate(a, RequiredScope.READ, null).outcome())
                .as("port READ %s", roles).isEqualTo(read ? Outcome.ALLOW : Outcome.DENY_ROLE);
        assertThat(adapter.evaluate(a, RequiredScope.WRITE, null).outcome())
                .as("port WRITE %s", roles).isEqualTo(write ? Outcome.ALLOW : Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("🔴 793 AC-1: masterdata set only → approval READ 403 and WRITE 403")
    void masterdataSetOnly_refusedApproval() {
        expect(MASTERDATA_SET, Set.of(), false, false);
    }

    @Test
    @DisplayName("🔴 793 AC-1: approval read set → READ allowed, WRITE (submit / approve / delegate) 403")
    void approvalReadSet_readOnly() {
        expect(APPROVAL_READ_SET, Set.of(), true, false);
    }

    @Test
    @DisplayName("🔴 793 AC-1: ERP_OPERATOR alone (entry role) → approval READ 403 and WRITE 403")
    void entryRoleAlone_opensNothing() {
        expect(Set.of("ERP_OPERATOR"), Set.of(), false, false);
    }

    @Test
    @DisplayName("🔴 793 Q1: entitled_domains ∋ erp with no role → READ 403")
    void subscriptionAlone_refused() {
        expect(Set.of(), Set.of("erp"), false, false);
    }

    @Test
    @DisplayName("🔴 793: ERP_APPROVAL_WRITE without the entry role → READ and WRITE allowed (the business role decides)")
    void approvalWrite_withoutEntry_allowed() {
        expect(Set.of("ERP_APPROVAL_WRITE"), Set.of(), true, true);
    }

    @Test
    @DisplayName("793 AC-1: approval set → READ and WRITE allowed")
    void approvalSet_allowed() {
        expect(APPROVAL_SET, Set.of(), true, true);
    }

    @Test
    @DisplayName("793 AC-2 control: legacy-compat token → READ and WRITE allowed, before and after")
    void legacyCompat_unchanged() {
        expect(LEGACY, Set.of("erp"), true, true);
    }

    @Test
    @DisplayName("793 control: workload scopes erp.read / erp.write keep their verdict")
    void workloadScopes_unchanged() {
        expect(Set.of("erp.read"), Set.of(), true, false);
        expect(Set.of("erp.write"), Set.of(), true, true);
    }

    @Test
    @DisplayName("793 Q4: isOperator() (tenant-wide list) still reads the entry role — unchanged")
    void isOperator_unchanged() {
        assertThat(actor(APPROVAL_READ_SET, Set.of()).isOperator()).isTrue();
        assertThat(actor(Set.of("ERP_APPROVAL_READ"), Set.of()).isOperator()).isFalse();
    }
}
