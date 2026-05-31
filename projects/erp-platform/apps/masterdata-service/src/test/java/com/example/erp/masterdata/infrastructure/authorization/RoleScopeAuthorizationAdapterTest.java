package com.example.erp.masterdata.infrastructure.authorization;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Authorization adapter unit — verifies E6 fail-CLOSED semantics. */
class RoleScopeAuthorizationAdapterTest {

    private final RoleScopeAuthorizationAdapter adapter = new RoleScopeAuthorizationAdapter();

    @Test
    @DisplayName("E6: no roles claim → DENY_ROLE")
    void noRolesDeny() {
        ActorContext actor = new ActorContext("u", "erp", null, null);
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("E6: empty roles + READ → DENY_ROLE")
    void emptyRolesReadDeny() {
        ActorContext actor = new ActorContext("u", "erp",
                Collections.emptySet(), Collections.emptySet());
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("E6: erp.read scope + READ + null target → ALLOW")
    void erpReadAllow() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.read"), Set.of("*"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("E6: erp.read + WRITE required → DENY_ROLE")
    void readScopeWriteOpDeny() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.read"), Set.of("*"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.WRITE, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("E6: erp.write + target outside data-scope → DENY_SCOPE")
    void targetOutsideScopeDeny() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("dept-allowed"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.WRITE, "dept-other");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    @Test
    @DisplayName("E6: erp.write + target in data-scope → ALLOW")
    void targetInScopeAllow() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("dept-a", "dept-b"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.WRITE, "dept-a");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("E6: platform-wide scope ('*') allows any target")
    void platformScopeAllow() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Set.of("*"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.WRITE, "dept-anywhere");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("E6: erp.write + no data-scope + non-null target → DENY_SCOPE fail-CLOSED")
    void emptyScopeWithTargetDeny() {
        ActorContext actor = new ActorContext("u", "erp",
                Set.of("erp.write"), Collections.emptySet());
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.WRITE, "dept-x");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    // ── ADR-MONO-019 § D5 entitlement-trust authz-layer dual-accept (MONO-161) ──

    @Test
    @DisplayName("entitlement-trust: entitled_domains ∋ 'erp' + NO role/scope + READ + null target → ALLOW")
    void entitledReadOverviewAllow() {
        // The operator-overview READ the BFF hits (listDepartments → null target):
        // a signed entitled_domains claim grants READ even with no erp.read/role.
        ActorContext actor = new ActorContext("u", "globex-corp",
                Collections.emptySet(), Collections.emptySet(), Set.of("erp"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.ALLOW);
    }

    @Test
    @DisplayName("entitlement-trust: entitled_domains ∋ 'erp' + NO role/scope + WRITE → DENY_ROLE (READ-only)")
    void entitledWriteStillDenied() {
        // Entitlement-trust grants READ visibility ONLY; WRITE stays scope/role
        // gated. An entitlement-only token attempting WRITE → still denied.
        ActorContext actor = new ActorContext("u", "globex-corp",
                Collections.emptySet(), Collections.emptySet(), Set.of("erp"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.WRITE, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("fail-closed: NO role/scope AND entitled_domains lacks 'erp' (only 'scm') + READ → DENY_ROLE")
    void notEntitledReadDeny() {
        // net-zero / fail-closed: neither a domain role/scope NOR entitlement to
        // 'erp' → READ denied (entitlement to a *different* domain does not grant erp).
        ActorContext actor = new ActorContext("u", "acme-corp",
                Collections.emptySet(), Collections.emptySet(), Set.of("scm"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("fail-closed: empty entitled_domains + NO role/scope + READ → DENY_ROLE")
    void emptyEntitlementReadDeny() {
        ActorContext actor = new ActorContext("u", "acme-corp",
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.READ, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("entitlement-trust READ does NOT loosen targeted data-scope: entitled + non-null target + no data-scope → DENY_SCOPE")
    void entitledTargetedReadStillScopeGated() {
        // The role gate now passes via entitlement, but a TARGETED read with no
        // data-scope still fails closed at the data-scope check (task: do not
        // loosen targeted data-scope beyond READ-overview).
        ActorContext actor = new ActorContext("u", "globex-corp",
                Collections.emptySet(), Collections.emptySet(), Set.of("erp"));
        AuthorizationDecision d = adapter.evaluate(actor, RequiredScope.READ, "dept-x");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }
}
