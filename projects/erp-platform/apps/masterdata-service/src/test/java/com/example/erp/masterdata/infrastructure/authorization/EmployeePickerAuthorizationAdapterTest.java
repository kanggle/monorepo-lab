package com.example.erp.masterdata.infrastructure.authorization;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision.Outcome;
import com.example.erp.masterdata.domain.authorization.ListDataScope;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-ERP-BE-045 — the picker's requirement ({@link RequiredScope#PICKER}) and its list data scope
 * ({@link RoleScopeAuthorizationAdapter#resolveListScope}).
 *
 * <p>The contract says the picker opens on «the same predicate as {@code GET /employees/{id}}» (793 Q3). The
 * strongest form of that claim is equality over every token shape we know of, so the first test compares the
 * two verdicts cell by cell instead of re-listing who is allowed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class EmployeePickerAuthorizationAdapterTest {

    @Mock
    private DepartmentRepository departmentRepository;

    private static final Set<String> ALL = Set.of("*");

    /** Every token shape the 793 tests use, plus the single-role cells of this ticket's control group. */
    private static final List<Set<String>> TOKENS = List.of(
            Set.of(),
            Set.of("ERP_OPERATOR"),
            Set.of("ERP_APPROVAL_READ"),
            Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ"),
            Set.of("ERP_APPROVAL_WRITE"),
            Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE"),
            Set.of("ERP_MASTERDATA_READ"),
            Set.of("ERP_MASTERDATA_WRITE"),
            Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE"),
            Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE",
                    "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE"),
            Set.of("erp.read"),
            Set.of("erp.write"),
            Set.of("ERP_ADMIN"),
            Set.of("SUPER_ADMIN"));

    private RoleScopeAuthorizationAdapter adapter() {
        return new RoleScopeAuthorizationAdapter(departmentRepository);
    }

    private Outcome verdict(String tenant, Set<String> roles, RequiredScope scope) {
        return adapter().evaluate(new ActorContext("op-1", tenant, roles, ALL, Set.of("erp")), scope, null)
                .outcome();
    }

    @Test
    @DisplayName("🔴 BE-045: PICKER verdict == SUBJECT_READ verdict for every known token shape (both tenants)")
    void pickerEqualsSubjectRead() {
        for (String tenant : List.of("demo-corp", "*")) {
            for (Set<String> roles : TOKENS) {
                assertThat(verdict(tenant, roles, RequiredScope.PICKER))
                        .as("tenant=%s roles=%s", tenant, roles)
                        .isEqualTo(verdict(tenant, roles, RequiredScope.SUBJECT_READ));
            }
        }
    }

    @Test
    @DisplayName("🔴 BE-045 AC-2 control group: no role 403 · approval read 403 · approval write 200 · masterdata read 200")
    void controlGroup() {
        assertThat(verdict("demo-corp", Set.of(), RequiredScope.PICKER)).isEqualTo(Outcome.DENY_ROLE);
        assertThat(verdict("demo-corp", Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ"), RequiredScope.PICKER))
                .isEqualTo(Outcome.DENY_ROLE);
        assertThat(verdict("demo-corp", Set.of("ERP_APPROVAL_WRITE"), RequiredScope.PICKER))
                .isEqualTo(Outcome.ALLOW);
        assertThat(verdict("demo-corp", Set.of("ERP_MASTERDATA_READ"), RequiredScope.PICKER))
                .isEqualTo(Outcome.ALLOW);
    }

    @Test
    @DisplayName("BE-045 control: PICKER does not widen READ — the approval writer is still refused READ")
    void readUnchanged() {
        assertThat(verdict("demo-corp", Set.of("ERP_APPROVAL_WRITE"), RequiredScope.READ))
                .isEqualTo(Outcome.DENY_ROLE);
    }

    // ---- resolveListScope ----------------------------------------------------------------------------

    @Test
    @DisplayName("BE-045 scope: org_scope ∋ '*' → TENANT, hierarchy not consulted")
    void platformScope() {
        ListDataScope s = adapter().resolveListScope(new ActorContext("op", "demo-corp",
                Set.of("ERP_APPROVAL_WRITE"), Set.of("*", "dept-A")));
        assertThat(s.kind()).isEqualTo(ListDataScope.Kind.TENANT);
        assertThat(s.isTenantWide()).isTrue();
        verifyNoInteractions(departmentRepository);
    }

    @Test
    @DisplayName("🔴 BE-045 scope: roots → SUBTREE = roots expanded ONCE through the hierarchy (one call, not N walks)")
    void rootsExpandedOnce() {
        when(departmentRepository.subtreeIds(Set.of("dept-A", "dept-B"), "demo-corp"))
                .thenReturn(Set.of("dept-A", "dept-A1", "dept-B"));
        ListDataScope s = adapter().resolveListScope(new ActorContext("op", "demo-corp",
                Set.of("ERP_APPROVAL_WRITE"), Set.of("dept-A", "dept-B")));
        assertThat(s.kind()).isEqualTo(ListDataScope.Kind.SUBTREE);
        assertThat(s.departmentIds()).containsExactlyInAnyOrder("dept-A", "dept-A1", "dept-B");
        assertThat(s.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("🔴 BE-045 scope: empty org_scope → SUBTREE with no department (fail-closed)")
    void emptyScope() {
        ListDataScope s = adapter().resolveListScope(new ActorContext("op", "demo-corp",
                Set.of("ERP_APPROVAL_WRITE"), Set.of()));
        assertThat(s.kind()).isEqualTo(ListDataScope.Kind.SUBTREE);
        assertThat(s.isEmpty()).isTrue();
        verifyNoInteractions(departmentRepository);
    }

    @Test
    @DisplayName("BE-045 scope: absent org_scope (null) / null actor → SUBTREE empty (fail-closed)")
    void absentScope() {
        assertThat(adapter().resolveListScope(new ActorContext("op", "demo-corp",
                Set.of("ERP_APPROVAL_WRITE"), null)).isEmpty()).isTrue();
        assertThat(adapter().resolveListScope(null).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("BE-045 scope: hierarchy port not wired → flat roots only (never widens) — same fallback as the single check")
    void noHierarchyFallsBackToRoots() {
        ListDataScope s = new RoleScopeAuthorizationAdapter(null).resolveListScope(new ActorContext("op",
                "demo-corp", Set.of("ERP_APPROVAL_WRITE"), Set.of("dept-A")));
        assertThat(s.departmentIds()).containsExactly("dept-A");
    }
}
