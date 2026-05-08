package com.wms.admin.application.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.fakes.InMemoryAssignmentRepository;
import com.wms.admin.application.fakes.InMemoryRoleRepository;
import com.wms.admin.application.fakes.InMemoryUserRepository;
import com.wms.admin.application.fakes.RecordingOutboxPort;
import com.wms.admin.domain.AssignmentStatus;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserStatus;
import com.wms.admin.domain.error.AssignmentNotFoundException;
import com.wms.admin.domain.error.RoleNotFoundException;
import com.wms.admin.domain.error.StateTransitionInvalidException;
import com.wms.admin.domain.error.UserNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssignmentServiceTest {

    private InMemoryUserRepository userRepo;
    private InMemoryRoleRepository roleRepo;
    private InMemoryAssignmentRepository assignmentRepo;
    private RecordingOutboxPort outbox;
    private AssignmentService service;

    private UUID activeUserId;
    private UUID activeRoleId;

    @BeforeEach
    void setUp() {
        userRepo = new InMemoryUserRepository();
        roleRepo = new InMemoryRoleRepository();
        assignmentRepo = new InMemoryAssignmentRepository();
        outbox = new RecordingOutboxPort();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(Instant.parse("2026-05-09T10:00:00Z"), ZoneOffset.UTC);
        service = new AssignmentService(assignmentRepo, userRepo, roleRepo, outbox,
                new AdminEventEnvelopeBuilder(mapper), fixed);

        // Seed an active user + role
        Instant t = Instant.parse("2026-05-09T10:00:00Z");
        User user = User.create(UUID.randomUUID(), "USR-1", "alice@example.com", "Alice",
                null, null, t, "system");
        User savedUser = userRepo.save(user);
        activeUserId = savedUser.id();

        Role role = Role.create(UUID.randomUUID(), "WMS_X", "X", null,
                "[\"INVENTORY_READ\"]", t, "system");
        roleRepo.seed(role);
        activeRoleId = role.id();
    }

    @Test
    void grant_freshTriple_createsAssignmentAndEmitsGranted() {
        GrantAssignmentResult result = service.grant(new GrantAssignmentCommand(
                activeUserId, activeRoleId, null, "admin"));
        assertThat(result.created()).isTrue();
        assertThat(result.assignment().status()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(outbox.eventTypes()).containsExactly("admin.assignment.granted");
    }

    @Test
    void grant_existingActiveTriple_returnsExisting_idempotent() {
        GrantAssignmentResult first = service.grant(new GrantAssignmentCommand(
                activeUserId, activeRoleId, null, "admin"));
        GrantAssignmentResult second = service.grant(new GrantAssignmentCommand(
                activeUserId, activeRoleId, null, "admin"));
        assertThat(second.created()).isFalse();
        assertThat(second.assignment().id()).isEqualTo(first.assignment().id());
        // No second granted event emitted.
        long granted = outbox.eventTypes().stream()
                .filter(t -> t.equals("admin.assignment.granted")).count();
        assertThat(granted).isEqualTo(1);
    }

    @Test
    void grant_userNotFound_raisesError() {
        assertThatThrownBy(() -> service.grant(new GrantAssignmentCommand(
                UUID.randomUUID(), activeRoleId, null, "admin")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void grant_roleNotFound_raisesError() {
        assertThatThrownBy(() -> service.grant(new GrantAssignmentCommand(
                activeUserId, UUID.randomUUID(), null, "admin")))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void grant_inactiveUser_raisesStateTransitionInvalid() {
        // Persist an INACTIVE user.
        User u = userRepo.findById(activeUserId).orElseThrow();
        userRepo.save(new com.wms.admin.domain.User(u.id(), u.userCode(), u.email(), u.name(),
                u.phone(), UserStatus.INACTIVE, u.defaultWarehouseId(), u.version(),
                u.createdAt(), u.createdBy(), u.updatedAt(), u.updatedBy()));
        assertThatThrownBy(() -> service.grant(new GrantAssignmentCommand(
                activeUserId, activeRoleId, null, "admin")))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void revoke_active_emitsRevokedEvent() {
        GrantAssignmentResult granted = service.grant(new GrantAssignmentCommand(
                activeUserId, activeRoleId, null, "admin"));
        service.revoke(granted.assignment().id(), "admin");
        assertThat(outbox.eventTypes())
                .containsExactly("admin.assignment.granted", "admin.assignment.revoked");
    }

    @Test
    void revoke_notFound_raisesError() {
        assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), "admin"))
                .isInstanceOf(AssignmentNotFoundException.class);
    }
}
