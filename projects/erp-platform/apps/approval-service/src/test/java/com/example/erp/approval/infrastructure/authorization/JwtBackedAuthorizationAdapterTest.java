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
    @DisplayName("data-scope: non-platform actor outside target subtree → DENY_SCOPE")
    void dataScopeDenied() {
        var d = adapter.evaluate(actor(Set.of("erp.write"), Set.of("dept-A"), Set.of()),
                RequiredScope.WRITE, "dept-B");
        assertThat(d.outcome()).isEqualTo(AuthorizationDecision.Outcome.DENY_SCOPE);
    }

    @Test
    @DisplayName("data-scope: actor within target subtree → ALLOW")
    void dataScopeAllowed() {
        var d = adapter.evaluate(actor(Set.of("erp.write"), Set.of("dept-A"), Set.of()),
                RequiredScope.WRITE, "dept-A");
        assertThat(d.allowed()).isTrue();
    }
}
