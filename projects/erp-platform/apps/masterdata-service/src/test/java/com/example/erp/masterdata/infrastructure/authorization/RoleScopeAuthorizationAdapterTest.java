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
}
