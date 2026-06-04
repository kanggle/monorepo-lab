package com.example.erp.masterdata.infrastructure.authorization;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/** Authorization adapter unit — verifies E6 fail-CLOSED + subtree-containment semantics. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RoleScopeAuthorizationAdapterTest {

    @Mock
    private DepartmentRepository departmentRepository;

    private RoleScopeAuthorizationAdapter adapter;

    private RoleScopeAuthorizationAdapter adapter() {
        if (adapter == null) {
            adapter = new RoleScopeAuthorizationAdapter(departmentRepository);
        }
        return adapter;
    }

    // ── existing role/scope + data-scope semantics (net-zero regression) ──

    @Test
    @DisplayName("E6: no roles claim → DENY_ROLE")
    void noRolesDeny() {
        ActorContext actor = new ActorContext("u", "erp", null, null);
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("E6: empty roles + READ → DENY_ROLE")
    void emptyRolesReadDeny() {
        ActorContext actor = new ActorContext("u", "erp",
                Collections.emptySet(), Collections.emptySet());
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("E6: erp.read scope + READ + null target → ALLOW")
    void erpReadAllow() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.read"), Set.of("*"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("E6: erp.read + WRITE required → DENY_ROLE")
    void readScopeWriteOpDeny() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.read"), Set.of("*"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("E6: platform-wide scope ('*') allows any target — no hierarchy walk")
    void platformScopeAllow() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("*"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "dept-anywhere");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("E6: erp.write + no data-scope (empty org_scope []) + non-null target → DENY_SCOPE fail-CLOSED")
    void emptyScopeWithTargetDeny() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Collections.emptySet());
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "dept-x");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    // ── subtree containment matrix (ADR-MONO-020 D3 amendment, TASK-ERP-BE-008) ──
    //
    // Tree (tenant "erp"):
    //   sales-root
    //     ├── sales-east
    //     │     └── sales-east-1
    //     └── sales-west
    //   eng-root
    //     └── eng-platform

    @Test
    @DisplayName("subtree: target == scoped root itself → ALLOW (no hierarchy walk needed)")
    void rootSelfInScope() {
        seedTree();
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("sales-root"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "sales-root");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("subtree: direct child of scoped root → ALLOW")
    void directDescendantInScope() {
        seedTree();
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("sales-root"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "sales-east");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("subtree: deep descendant (grandchild) of scoped root → ALLOW")
    void deepDescendantInScope() {
        seedTree();
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("sales-root"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "sales-east-1");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("subtree: sibling subtree (eng-root tree) → DENY_SCOPE")
    void siblingSubtreeOutOfScope() {
        seedTree();
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("sales-root"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "eng-platform");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    @Test
    @DisplayName("subtree: ancestor-of-root is NOT in scope (root narrows downward only) → DENY_SCOPE")
    void ancestorOfRootOutOfScope() {
        // Tree gains a super-root above sales-root; scoping sales-root must NOT
        // grant the super-root (scope is downward subtree, not upward).
        Map<String, Department> tree = new HashMap<>();
        put(tree, "super-root", null);
        put(tree, "sales-root", "super-root");
        put(tree, "sales-east", "sales-root");
        wire(tree);
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("sales-root"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "super-root");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    @Test
    @DisplayName("subtree: multiple scoped roots — target under the SECOND root → ALLOW")
    void unionOfScopedRoots() {
        seedTree();
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("sales-root", "eng-root"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "eng-platform");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("subtree: cycle-free walk — unrelated deep target terminates and DENY_SCOPE")
    void cycleFreeWalkTerminates() {
        // sales-east-1 → sales-east → sales-root (tree root). scoping eng-root
        // → none of the sales chain matches → DENY (walk terminates at root).
        seedTree();
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("eng-root"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "sales-east-1");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    @Test
    @DisplayName("subtree: target whose department resolves to empty ancestor chain (unresolved) → DENY_SCOPE")
    void unresolvedTargetDeny() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("sales-root"));
        // org_scope does not contain the target and the hierarchy yields no
        // ancestors (target not projected) → conservative deny.
        lenient().when(departmentRepository.ancestors("ghost-dept", "erp"))
                .thenReturn(List.of());
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, "ghost-dept");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    // ── ADR-MONO-019 § D5 entitlement-trust authz-layer dual-accept (MONO-161) ──

    @Test
    @DisplayName("entitlement-trust: entitled_domains ∋ 'erp' + NO role/scope + READ + null target → ALLOW")
    void entitledReadOverviewAllow() {
        ActorContext actor = new ActorContext("u", "globex-corp",
                Collections.emptySet(), Collections.emptySet(), Set.of("erp"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("entitlement-trust: entitled_domains ∋ 'erp' + NO role/scope + WRITE → DENY_ROLE (READ-only)")
    void entitledWriteStillDenied() {
        ActorContext actor = new ActorContext("u", "globex-corp",
                Collections.emptySet(), Collections.emptySet(), Set.of("erp"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.WRITE, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("fail-closed: NO role/scope AND entitled_domains lacks 'erp' (only 'scm') + READ → DENY_ROLE")
    void notEntitledReadDeny() {
        ActorContext actor = new ActorContext("u", "acme-corp",
                Collections.emptySet(), Collections.emptySet(), Set.of("scm"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("fail-closed: empty entitled_domains + NO role/scope + READ → DENY_ROLE")
    void emptyEntitlementReadDeny() {
        ActorContext actor = new ActorContext("u", "acme-corp",
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("entitlement-trust READ does NOT loosen targeted data-scope: entitled + non-null target + no data-scope → DENY_SCOPE")
    void entitledTargetedReadStillScopeGated() {
        ActorContext actor = new ActorContext("u", "globex-corp",
                Collections.emptySet(), Collections.emptySet(), Set.of("erp"));
        AuthorizationDecision d = adapter().evaluate(actor, RequiredScope.READ, "dept-x");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    // ── test fixture helpers ──

    private void seedTree() {
        Map<String, Department> tree = new HashMap<>();
        put(tree, "sales-root", null);
        put(tree, "sales-east", "sales-root");
        put(tree, "sales-east-1", "sales-east");
        put(tree, "sales-west", "sales-root");
        put(tree, "eng-root", null);
        put(tree, "eng-platform", "eng-root");
        wire(tree);
    }

    private void put(Map<String, Department> tree, String id, String parentId) {
        tree.put(id, Department.create(id, "erp", "code-" + id, "name-" + id,
                parentId, new EffectivePeriod(LocalDate.now(), null), Instant.now()));
    }

    /**
     * Stubs {@code ancestors(target, "erp")} to return [target, parent, …,
     * root] per the tree map — mirroring {@code DepartmentRepositoryImpl}'s
     * walk. {@code lenient()} so a test that resolves in-scope via the fast
     * exact-match path (root self) does not trip STRICT_STUBS on the unused
     * walk stub.
     */
    private void wire(Map<String, Department> tree) {
        for (String id : tree.keySet()) {
            List<Department> chain = new ArrayList<>();
            String cur = id;
            int bound = 100;
            while (cur != null && bound-- > 0) {
                Department d = tree.get(cur);
                if (d == null) {
                    break;
                }
                chain.add(d);
                cur = d.getParentId();
            }
            lenient().when(departmentRepository.ancestors(id, "erp")).thenReturn(chain);
        }
    }
}
