package com.example.fanplatform.artist.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ActorContext#isAdmin()} role mapping. The positive case that matters is the
 * assume-tenant {@code FAN_OPERATOR} — before TASK-MONO-417 the check (and the URL-level
 * {@code ADMIN_ROLES} in SecurityConfig) saw only the generic triple, so a cross-tenant
 * operator was silently denied admin-tier actions.
 */
class ActorContextTest {

    private static ActorContext actor(String... roles) {
        return new ActorContext("acc-1", "fan-platform", Set.of(roles));
    }

    @Test
    @DisplayName("assume-tenant FAN_OPERATOR → admin-tier (TASK-MONO-417)")
    void prefixedOperatorRoleIsAdmin() {
        assertThat(actor("FAN_OPERATOR").isAdmin()).isTrue();
    }

    @Test
    @DisplayName("generic admin-tier roles still admin (regression)")
    void genericAdminRolesStillAdmin() {
        assertThat(actor("ADMIN").isAdmin()).isTrue();
        assertThat(actor("OPERATOR").isAdmin()).isTrue();
        assertThat(actor("SUPER_ADMIN").isAdmin()).isTrue();
    }

    @Test
    @DisplayName("consumer / no admin role → not admin")
    void nonAdminIsNotAdmin() {
        assertThat(actor("FAN").isAdmin()).isFalse();
        assertThat(actor().isAdmin()).isFalse();
    }
}
