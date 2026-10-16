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
 *
 * <p>TASK-MONO-794 (ADR-083 P7): {@code FINANCE_OPERATOR} is now the auth-derived entry role, riding with ≥1 Finance
 * business role; either marks the caller an operator for audit. The generic {@code OPERATOR/ADMIN/SUPER_ADMIN}
 * strings are issued by nobody and no longer count (Failure 1).
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
    @DisplayName("🔴 794 Failure 1: the never-issued generic OPERATOR / ADMIN / SUPER_ADMIN are NOT operators any more")
    void genericOperatorRolesNoLongerOperator() {
        assertThat(actor("OPERATOR").isOperator()).isFalse();
        assertThat(actor("ADMIN").isOperator()).isFalse();
        assertThat(actor("SUPER_ADMIN").isOperator()).isFalse();
        assertThat(actor("OPERATOR").actorType()).isEqualTo(ActorType.HOLDER);
    }

    @Test
    @DisplayName("794: any Finance business role marks an operator (the derived entry role rides with it in production)")
    void financeBusinessRoleIsOperator() {
        for (String role : java.util.List.of("FINANCE_ACCOUNT_READ", "FINANCE_ACCOUNT_WRITE", "FINANCE_LEDGER_READ",
                "FINANCE_LEDGER_WRITE", "FINANCE_LEDGER_CONTROL")) {
            assertThat(actor(role).isOperator()).as(role).isTrue();
        }
    }

    @Test
    @DisplayName("no operator role → HOLDER")
    void nonOperatorIsHolder() {
        assertThat(actor("CUSTOMER").isOperator()).isFalse();
        assertThat(actor("CUSTOMER").actorType()).isEqualTo(ActorType.HOLDER);
        assertThat(actor().isOperator()).isFalse();
    }
}
