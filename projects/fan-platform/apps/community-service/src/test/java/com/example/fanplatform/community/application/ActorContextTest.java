package com.example.fanplatform.community.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ActorContext#isOperator()} role mapping. The positive case that matters is the
 * assume-tenant {@code FAN_OPERATOR} — before TASK-MONO-417 the check saw only the generic
 * triple, so a cross-tenant operator was silently treated as a non-operator.
 */
class ActorContextTest {

    private static ActorContext actor(String... roles) {
        return new ActorContext("acc-1", "fan-platform", Set.of(roles));
    }

    @Test
    @DisplayName("assume-tenant FAN_OPERATOR → operator (TASK-MONO-417)")
    void prefixedOperatorRoleIsOperator() {
        assertThat(actor("FAN_OPERATOR").isOperator()).isTrue();
    }

    @Test
    @DisplayName("directly-provisioned generic operator still operator (regression)")
    void genericOperatorRolesStillOperator() {
        assertThat(actor("OPERATOR").isOperator()).isTrue();
        assertThat(actor("ADMIN").isOperator()).isTrue();
        assertThat(actor("SUPER_ADMIN").isOperator()).isTrue();
    }

    @Test
    @DisplayName("consumer / no operator role → not operator")
    void nonOperatorIsNotOperator() {
        assertThat(actor("FAN").isOperator()).isFalse();
        assertThat(actor().isOperator()).isFalse();
    }
}
