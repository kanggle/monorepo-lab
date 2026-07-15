package com.example.scmplatform.procurement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.scmplatform.procurement.domain.po.status.ActorType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ActorContext#isOperator()} role mapping. The positive case that matters is the
 * assume-tenant {@code SCM_OPERATOR} — before TASK-MONO-417 the check saw only the generic
 * triple, so a cross-tenant operator was silently mapped to {@link ActorType#BUYER}.
 */
class ActorContextTest {

    private static ActorContext actor(String... roles) {
        return new ActorContext("acc-1", "scm", Set.of(roles));
    }

    @Test
    @DisplayName("assume-tenant SCM_OPERATOR → operator (TASK-MONO-417)")
    void prefixedOperatorRoleIsOperator() {
        assertThat(actor("SCM_OPERATOR").isOperator()).isTrue();
        assertThat(actor("SCM_OPERATOR").actorType()).isEqualTo(ActorType.OPERATOR);
    }

    @Test
    @DisplayName("directly-provisioned generic operator still operator (regression)")
    void genericOperatorRolesStillOperator() {
        assertThat(actor("OPERATOR").isOperator()).isTrue();
        assertThat(actor("ADMIN").isOperator()).isTrue();
        assertThat(actor("SUPER_ADMIN").isOperator()).isTrue();
    }

    @Test
    @DisplayName("no operator role → BUYER")
    void nonOperatorIsBuyer() {
        assertThat(actor("BUYER").isOperator()).isFalse();
        assertThat(actor("BUYER").actorType()).isEqualTo(ActorType.BUYER);
        assertThat(actor().isOperator()).isFalse();
    }
}
