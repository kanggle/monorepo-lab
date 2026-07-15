package com.example.finance.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finance.account.domain.account.ActorType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ActorContext#isOperator()} role mapping. The positive case that matters is the
 * assume-tenant {@code FINANCE_OPERATOR} — before TASK-MONO-417 the check saw only the generic
 * triple, so a cross-tenant operator was silently mapped to {@link ActorType#HOLDER}.
 */
class ActorContextTest {

    private static ActorContext actor(String... roles) {
        return new ActorContext("acc-1", "finance", Set.of(roles));
    }

    @Test
    @DisplayName("assume-tenant FINANCE_OPERATOR → operator (TASK-MONO-417)")
    void prefixedOperatorRoleIsOperator() {
        assertThat(actor("FINANCE_OPERATOR").isOperator()).isTrue();
        assertThat(actor("FINANCE_OPERATOR").actorType()).isEqualTo(ActorType.OPERATOR);
    }

    @Test
    @DisplayName("directly-provisioned generic operator still operator (regression)")
    void genericOperatorRolesStillOperator() {
        assertThat(actor("OPERATOR").isOperator()).isTrue();
        assertThat(actor("ADMIN").isOperator()).isTrue();
        assertThat(actor("SUPER_ADMIN").isOperator()).isTrue();
    }

    @Test
    @DisplayName("no operator role → HOLDER")
    void nonOperatorIsHolder() {
        assertThat(actor("CUSTOMER").isOperator()).isFalse();
        assertThat(actor("CUSTOMER").actorType()).isEqualTo(ActorType.HOLDER);
        assertThat(actor().isOperator()).isFalse();
    }
}
