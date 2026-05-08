package com.wms.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.admin.domain.error.StateTransitionInvalidException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentInvariantTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    void grant_setsActiveStatusAndGrantedFields() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UserRoleAssignment a = UserRoleAssignment.grant(
                UUID.randomUUID(), userId, roleId, warehouseId, NOW, "admin");
        assertThat(a.status()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(a.userId()).isEqualTo(userId);
        assertThat(a.roleId()).isEqualTo(roleId);
        assertThat(a.warehouseId()).isEqualTo(warehouseId);
        assertThat(a.grantedAt()).isEqualTo(NOW);
        assertThat(a.grantedBy()).isEqualTo("admin");
        assertThat(a.revokedAt()).isNull();
    }

    @Test
    void grant_withNullWarehouse_isGlobalScope() {
        UserRoleAssignment a = UserRoleAssignment.grant(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, NOW, "admin");
        assertThat(a.warehouseId()).isNull();
    }

    @Test
    void revoke_fromActive_terminalState() {
        UserRoleAssignment a = UserRoleAssignment.grant(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, NOW, "admin");
        UserRoleAssignment revoked = a.revoke(NOW.plusSeconds(60), "admin2");
        assertThat(revoked.status()).isEqualTo(AssignmentStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(revoked.revokedBy()).isEqualTo("admin2");
    }

    @Test
    void revoke_fromRevoked_rejected_terminalState() {
        UserRoleAssignment a = UserRoleAssignment.grant(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, NOW, "admin");
        UserRoleAssignment revoked = a.revoke(NOW, "admin2");
        assertThatThrownBy(() -> revoked.revoke(NOW.plusSeconds(60), "admin3"))
                .isInstanceOf(StateTransitionInvalidException.class)
                .hasMessageContaining("REVOKED");
    }

    @Test
    void grant_requiresNonNullUserAndRole() {
        UUID assignId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        assertThatThrownBy(() ->
                new UserRoleAssignment(assignId, null, roleId, null,
                        NOW, "admin", null, null,
                        AssignmentStatus.ACTIVE, 0L, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void revoke_preservesGrantContext() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UserRoleAssignment a = UserRoleAssignment.grant(id, userId, roleId, null, NOW, "admin1");
        UserRoleAssignment revoked = a.revoke(NOW.plusSeconds(60), "admin2");
        assertThat(revoked.id()).isEqualTo(id);
        assertThat(revoked.userId()).isEqualTo(userId);
        assertThat(revoked.roleId()).isEqualTo(roleId);
        assertThat(revoked.grantedBy()).isEqualTo("admin1");
    }
}
