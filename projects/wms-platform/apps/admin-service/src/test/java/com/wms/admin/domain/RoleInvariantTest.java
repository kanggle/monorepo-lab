package com.wms.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.admin.domain.error.RoleBuiltinImmutableException;
import com.wms.admin.domain.error.StateTransitionInvalidException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleInvariantTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");
    private static final String ACTOR = "admin";
    private static final String DEFAULT_PERMS = "[\"INVENTORY_READ\"]";

    @Test
    void create_setsActiveAndNonBuiltin() {
        Role r = Role.create(UUID.randomUUID(), "WMS_SHIFT_LEAD", "Shift Lead", "desc",
                DEFAULT_PERMS, NOW, ACTOR);
        assertThat(r.status()).isEqualTo(RoleStatus.ACTIVE);
        assertThat(r.isBuiltin()).isFalse();
        assertThat(r.roleCode()).isEqualTo("WMS_SHIFT_LEAD");
    }

    @Test
    void builtin_deactivate_rejectedWithBuiltinImmutable() {
        // Simulate a seeded built-in role.
        Role builtin = new Role(UUID.randomUUID(), "WMS_ADMIN", "Admin", null,
                DEFAULT_PERMS, RoleStatus.ACTIVE, true, 0L,
                NOW, "system", NOW, "system");
        assertThatThrownBy(() -> builtin.deactivate(NOW, ACTOR))
                .isInstanceOf(RoleBuiltinImmutableException.class)
                .hasMessageContaining("WMS_ADMIN");
    }

    @Test
    void custom_deactivate_succeeds() {
        Role r = Role.create(UUID.randomUUID(), "WMS_SHIFT_LEAD", "Shift Lead", null,
                DEFAULT_PERMS, NOW, ACTOR);
        Role d = r.deactivate(NOW.plusSeconds(60), ACTOR);
        assertThat(d.status()).isEqualTo(RoleStatus.INACTIVE);
    }

    @Test
    void deactivate_fromInactive_rejected() {
        Role r = Role.create(UUID.randomUUID(), "WMS_X", "X", null, DEFAULT_PERMS, NOW, ACTOR);
        Role d = r.deactivate(NOW, ACTOR);
        assertThatThrownBy(() -> d.deactivate(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void reactivate_fromActive_rejected() {
        Role r = Role.create(UUID.randomUUID(), "WMS_X", "X", null, DEFAULT_PERMS, NOW, ACTOR);
        assertThatThrownBy(() -> r.reactivate(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void builtinCodes_listContainsAllFour() {
        assertThat(Role.BUILTIN_CODES).containsExactly(
                "WMS_VIEWER", "WMS_OPERATOR", "WMS_ADMIN", "WMS_SUPERADMIN");
    }
}
