package com.example.erp.masterdata.infrastructure.authorization;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision.Outcome;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions Q1 · roles, 2026-10-15 UTC) — the masterdata business-role
 * verdict (erp iam-integration.md § 업무 역할). Each token below is the shape auth-service actually mints: the
 * entry role {@code ERP_OPERATOR} is DERIVED whenever ≥1 ERP business role rides the token, so a «set holder»
 * token carries it too — which is exactly why the entry role must not open masterdata on its own.
 *
 * <p>Controls (failing side first): an approval-only, an entry-only and a subscription-only token are refused;
 * a masterdata read set is refused WRITE. Legacy-compat (every ERP role) and the workload {@code erp.write}
 * scope keep their verdict (AC-2). Data scope is the unchanged second check — targets here are {@code null}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MasterdataBusinessRoleAdapterTest {

    @Mock
    private DepartmentRepository departmentRepository;

    private static final Set<String> ALL = Set.of("*");

    static final Set<String> LEGACY = Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE",
            "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE");
    static final Set<String> MASTERDATA_READ_SET = Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ");
    static final Set<String> MASTERDATA_OPERATE_SET = Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ",
            "ERP_MASTERDATA_WRITE");
    static final Set<String> APPROVAL_SET = Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE");
    static final Set<String> ENTRY_ONLY = Set.of("ERP_OPERATOR");

    private Outcome verdict(Set<String> roles, Set<String> entitled, RequiredScope scope) {
        return new RoleScopeAuthorizationAdapter(departmentRepository)
                .evaluate(new ActorContext("op-1", "demo-corp", roles, ALL, entitled), scope, null)
                .outcome();
    }

    @Test
    @DisplayName("🔴 793 AC-1: approval set only → masterdata READ 403 and WRITE 403")
    void approvalSetOnly_refusedMasterdata() {
        assertThat(verdict(APPROVAL_SET, Set.of(), RequiredScope.READ)).isEqualTo(Outcome.DENY_ROLE);
        assertThat(verdict(APPROVAL_SET, Set.of(), RequiredScope.WRITE)).isEqualTo(Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("🔴 793 AC-1: masterdata read set → READ allowed, WRITE 403")
    void readSet_readOnly() {
        assertThat(verdict(MASTERDATA_READ_SET, Set.of(), RequiredScope.READ)).isEqualTo(Outcome.ALLOW);
        assertThat(verdict(MASTERDATA_READ_SET, Set.of(), RequiredScope.WRITE)).isEqualTo(Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("🔴 793 AC-1: ERP_OPERATOR alone (entry role) → masterdata READ 403 and WRITE 403")
    void entryRoleAlone_opensNothing() {
        assertThat(verdict(ENTRY_ONLY, Set.of(), RequiredScope.READ)).isEqualTo(Outcome.DENY_ROLE);
        assertThat(verdict(ENTRY_ONLY, Set.of(), RequiredScope.WRITE)).isEqualTo(Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("🔴 793 Q1: entitled_domains ∋ erp with no role → READ 403 (the subscription no longer reads)")
    void subscriptionAlone_readRefused() {
        assertThat(verdict(Set.of(), Set.of("erp"), RequiredScope.READ)).isEqualTo(Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("793: masterdata operate set without the entry role → READ and WRITE allowed (the business role decides)")
    void operateSet_withoutEntry_allowed() {
        Set<String> noEntry = Set.of("ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE");
        assertThat(verdict(noEntry, Set.of(), RequiredScope.READ)).isEqualTo(Outcome.ALLOW);
        assertThat(verdict(noEntry, Set.of(), RequiredScope.WRITE)).isEqualTo(Outcome.ALLOW);
        assertThat(verdict(MASTERDATA_OPERATE_SET, Set.of(), RequiredScope.WRITE)).isEqualTo(Outcome.ALLOW);
    }

    @Test
    @DisplayName("793 AC-2 control: legacy-compat token (every ERP role) → READ and WRITE allowed, before and after")
    void legacyCompat_unchanged() {
        assertThat(verdict(LEGACY, Set.of("erp"), RequiredScope.READ)).isEqualTo(Outcome.ALLOW);
        assertThat(verdict(LEGACY, Set.of("erp"), RequiredScope.WRITE)).isEqualTo(Outcome.ALLOW);
    }

    @Test
    @DisplayName("793 control: the workload scopes erp.read / erp.write keep their verdict (machine client unchanged)")
    void workloadScopes_unchanged() {
        assertThat(verdict(Set.of("erp.read"), Set.of(), RequiredScope.READ)).isEqualTo(Outcome.ALLOW);
        assertThat(verdict(Set.of("erp.read"), Set.of(), RequiredScope.WRITE)).isEqualTo(Outcome.DENY_ROLE);
        assertThat(verdict(Set.of("erp.write"), Set.of(), RequiredScope.WRITE)).isEqualTo(Outcome.ALLOW);
    }

    @Test
    @DisplayName("793 edge: tenant_id='*' with no role → READ allowed, WRITE 403 (TASK-ERP-BE-031 unchanged)")
    void wildcardRead_unchanged() {
        RoleScopeAuthorizationAdapter adapter = new RoleScopeAuthorizationAdapter(departmentRepository);
        ActorContext star = new ActorContext("super", "*", Set.of(), Set.of(), Set.of());
        assertThat(adapter.evaluate(star, RequiredScope.READ, null).outcome()).isEqualTo(Outcome.ALLOW);
        assertThat(adapter.evaluate(star, RequiredScope.WRITE, null).outcome()).isEqualTo(Outcome.DENY_ROLE);
    }
}
