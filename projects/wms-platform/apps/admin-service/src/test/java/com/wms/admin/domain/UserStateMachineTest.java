package com.wms.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.admin.domain.error.StateTransitionInvalidException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");
    private static final String ACTOR = "admin-uuid";

    @Test
    void create_setsActiveStatus() {
        User u = User.create(UUID.randomUUID(), "USR-1", "alice@example.com", "Alice",
                null, null, NOW, ACTOR);
        assertThat(u.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(u.email()).isEqualTo("alice@example.com");
        assertThat(u.userCode()).isEqualTo("USR-1");
        assertThat(u.version()).isZero();
    }

    @Test
    void deactivate_fromActive_succeeds() {
        User u = User.create(UUID.randomUUID(), "USR-1", "a@b.com", "A", null, null, NOW, ACTOR);
        User d = u.deactivate(NOW.plusSeconds(60), ACTOR);
        assertThat(d.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(d.id()).isEqualTo(u.id());
        assertThat(d.userCode()).isEqualTo(u.userCode());
    }

    @Test
    void deactivate_fromInactive_rejected() {
        User u = User.create(UUID.randomUUID(), "USR-1", "a@b.com", "A", null, null, NOW, ACTOR);
        User inactive = u.deactivate(NOW, ACTOR);
        assertThatThrownBy(() -> inactive.deactivate(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class)
                .hasMessageContaining("INACTIVE");
    }

    @Test
    void reactivate_fromInactive_succeeds() {
        User u = User.create(UUID.randomUUID(), "USR-1", "a@b.com", "A", null, null, NOW, ACTOR);
        User inactive = u.deactivate(NOW, ACTOR);
        User reactivated = inactive.reactivate(NOW.plusSeconds(120), ACTOR);
        assertThat(reactivated.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void reactivate_fromActive_rejected() {
        User u = User.create(UUID.randomUUID(), "USR-1", "a@b.com", "A", null, null, NOW, ACTOR);
        assertThatThrownBy(() -> u.reactivate(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void updateProfile_preservesIdAndUserCode() {
        UUID id = UUID.randomUUID();
        User u = User.create(id, "USR-1", "a@b.com", "A", "+82-1", null, NOW, ACTOR);
        User updated = u.updateProfile("Alice L", "alice.l@b.com", "+82-2", null, NOW, ACTOR);
        assertThat(updated.id()).isEqualTo(id);
        assertThat(updated.userCode()).isEqualTo("USR-1");
        assertThat(updated.name()).isEqualTo("Alice L");
        assertThat(updated.email()).isEqualTo("alice.l@b.com");
    }

    @Test
    void updateProfile_nullFields_keepExistingValues() {
        User u = User.create(UUID.randomUUID(), "USR-1", "a@b.com", "A", "+82-1", null, NOW, ACTOR);
        User updated = u.updateProfile(null, null, null, null, NOW, ACTOR);
        assertThat(updated.name()).isEqualTo("A");
        assertThat(updated.email()).isEqualTo("a@b.com");
        assertThat(updated.phone()).isEqualTo("+82-1");
    }

    @Test
    void create_requiresEmailUserCodeAndName() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> User.create(id, null, "a@b.com", "A", null, null, NOW, ACTOR))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> User.create(id, "U-1", null, "A", null, null, NOW, ACTOR))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> User.create(id, "U-1", "a@b.com", null, null, null, NOW, ACTOR))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deactivateReactivateCycle_preservesAuditFields() {
        UUID id = UUID.randomUUID();
        Instant t0 = NOW;
        User u = User.create(id, "USR-1", "a@b.com", "A", null, null, t0, ACTOR);
        User deactivated = u.deactivate(t0.plusSeconds(60), "admin2");
        User reactivated = deactivated.reactivate(t0.plusSeconds(120), "admin3");
        assertThat(reactivated.createdAt()).isEqualTo(t0);
        assertThat(reactivated.createdBy()).isEqualTo(ACTOR);
        assertThat(reactivated.updatedBy()).isEqualTo("admin3");
    }
}
