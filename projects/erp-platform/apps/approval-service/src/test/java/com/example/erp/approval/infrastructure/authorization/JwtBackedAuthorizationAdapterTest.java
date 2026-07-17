package com.example.erp.approval.infrastructure.authorization;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.domain.authorization.AuthorizationDecision;
import com.example.erp.approval.domain.authorization.RequiredScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtBackedAuthorizationAdapterTest {

    private final JwtBackedAuthorizationAdapter adapter = new JwtBackedAuthorizationAdapter();

    private ActorContext actor(Set<String> roles, Set<String> scope, Set<String> entitled) {
        return new ActorContext("emp-1", "erp", roles, scope, entitled);
    }

    @Test
    @DisplayName("WRITE: erp.write → ALLOW")
    void writeWithScope() {
        var d = adapter.evaluate(actor(Set.of("erp.write"), Set.of("*"), Set.of()),
                RequiredScope.WRITE, null);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    @DisplayName("WRITE: no role → DENY_ROLE (fail-closed)")
    void writeNoRole() {
        var d = adapter.evaluate(actor(Set.of(), Set.of(), Set.of()),
                RequiredScope.WRITE, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("READ: entitlement-trust grants READ even without erp.read")
    void readByEntitlement() {
        var d = adapter.evaluate(actor(Set.of(), Set.of(), Set.of("erp")),
                RequiredScope.READ, null);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    @DisplayName("WRITE: entitlement-trust does NOT widen a transition")
    void entitlementNeverWidensWrite() {
        var d = adapter.evaluate(actor(Set.of(), Set.of(), Set.of("erp")),
                RequiredScope.WRITE, null);
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_ROLE);
    }

    @Test
    @DisplayName("v1: data-scope NOT enforced — narrow-scope actor with an out-of-subtree target is ALLOWED (deferred to v2 permission-service, TASK-ERP-BE-030)")
    void dataScopeNotEnforcedInV1() {
        // Even with a non-null target department OUTSIDE the actor's scope, v1 allows: the JWT-backed
        // adapter enforces role/scope only. Subject owning-department subtree confinement is a v2
        // permission-service concern. This is the honest inverse of the removed (unreachable,
        // fail-open) data-scope branch — it guards against silently re-introducing a broken check.
        var d = adapter.evaluate(actor(Set.of("erp.write"), Set.of("dept-A"), Set.of()),
                RequiredScope.WRITE, "dept-B");
        assertThat(d.allowed())
                .as("v1 approval does not confine by subject owning-department; subtree enforcement is a v2 concern")
                .isTrue();
    }
}
