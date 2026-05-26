package com.wms.admin.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.assignment.AssignmentEventHelper;
import com.wms.admin.application.fakes.InMemoryAssignmentRepository;
import com.wms.admin.application.fakes.InMemoryUserRepository;
import com.wms.admin.application.fakes.RecordingOutboxPort;
import com.wms.admin.domain.AssignmentStatus;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserRoleAssignment;
import com.wms.admin.domain.UserStatus;
import com.wms.admin.domain.error.UserEmailDuplicateException;
import com.wms.admin.domain.error.UserHasActiveAssignmentsException;
import com.wms.admin.domain.error.UserNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class UserServiceTest {

    private InMemoryUserRepository userRepo;
    private InMemoryAssignmentRepository assignmentRepo;
    private RecordingOutboxPort outbox;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepo = new InMemoryUserRepository();
        assignmentRepo = new InMemoryAssignmentRepository();
        outbox = new RecordingOutboxPort();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(Instant.parse("2026-05-09T10:00:00Z"), ZoneOffset.UTC);
        AdminEventEnvelopeBuilder envelopeBuilder = new AdminEventEnvelopeBuilder(mapper);
        service = new UserService(userRepo, assignmentRepo, outbox,
                envelopeBuilder, new AssignmentEventHelper(assignmentRepo, outbox, envelopeBuilder), fixed);
    }

    @Test
    void create_happyPath_savesUserAndEmitsCreatedEvent() {
        User saved = service.create(new CreateUserCommand(
                "USR-1", "Alice@example.com", "Alice", "+82-1", null, "admin"));
        assertThat(saved.email()).isEqualTo("alice@example.com"); // lowercased
        assertThat(saved.userCode()).isEqualTo("USR-1");
        assertThat(saved.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(outbox.eventTypes()).containsExactly("admin.user.created");
    }

    @Test
    void create_duplicateEmail_raisesUserEmailDuplicate() {
        service.create(new CreateUserCommand("USR-1", "alice@example.com", "Alice", null, null, "admin"));
        assertThatThrownBy(() -> service.create(
                new CreateUserCommand("USR-2", "alice@example.com", "B", null, null, "admin")))
                .isInstanceOf(UserEmailDuplicateException.class);
    }

    @Test
    void create_duplicateEmail_caseInsensitive() {
        service.create(new CreateUserCommand("USR-1", "Alice@example.com", "Alice", null, null, "admin"));
        assertThatThrownBy(() -> service.create(
                new CreateUserCommand("USR-2", "ALICE@EXAMPLE.COM", "B", null, null, "admin")))
                .isInstanceOf(UserEmailDuplicateException.class);
    }

    @Test
    void update_emitsUpdatedEvent_whenFieldsChange() {
        User user = service.create(
                new CreateUserCommand("USR-1", "alice@example.com", "Alice", null, null, "admin"));
        User updated = service.update(new UpdateUserCommand(
                user.id(), "Alice L", null, null, null, "admin"));
        assertThat(updated.name()).isEqualTo("Alice L");
        assertThat(outbox.eventTypes()).contains("admin.user.created", "admin.user.updated");
    }

    @Test
    void update_notFound_raisesUserNotFound() {
        assertThatThrownBy(() -> service.update(
                new UpdateUserCommand(UUID.randomUUID(), "x", null, null, null, "admin")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deactivate_noActiveAssignments_succeeds() {
        User user = service.create(
                new CreateUserCommand("USR-1", "alice@example.com", "Alice", null, null, "admin"));
        DeactivateUserResult result = service.deactivate(
                new DeactivateUserCommand(user.id(), false, "admin", false));
        assertThat(result.user().status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(result.revokedAssignmentIds()).isEmpty();
        assertThat(outbox.eventTypes()).contains("admin.user.deactivated");
    }

    @Test
    void deactivate_withActiveAssignmentsAndForceFalse_raisesError() {
        User user = service.create(
                new CreateUserCommand("USR-1", "alice@example.com", "Alice", null, null, "admin"));
        seedAssignment(user.id(), UUID.randomUUID());

        assertThatThrownBy(() -> service.deactivate(
                new DeactivateUserCommand(user.id(), false, "admin", false)))
                .isInstanceOf(UserHasActiveAssignmentsException.class);
    }

    @Test
    void deactivate_withForceTrueButNotSuperadmin_raisesAccessDenied() {
        User user = service.create(
                new CreateUserCommand("USR-1", "alice@example.com", "Alice", null, null, "admin"));
        seedAssignment(user.id(), UUID.randomUUID());

        assertThatThrownBy(() -> service.deactivate(
                new DeactivateUserCommand(user.id(), true, "admin", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deactivate_forceCascade_revokesAssignmentsAndEmitsRevokedPlusDeactivatedEvents() {
        User user = service.create(
                new CreateUserCommand("USR-1", "alice@example.com", "Alice", null, null, "admin"));
        seedAssignment(user.id(), UUID.randomUUID());
        seedAssignment(user.id(), UUID.randomUUID());

        DeactivateUserResult result = service.deactivate(
                new DeactivateUserCommand(user.id(), true, "superadmin", true));
        assertThat(result.user().status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(result.revokedAssignmentIds()).hasSize(2);
        long revokedEvents = outbox.eventTypes().stream()
                .filter(t -> t.equals("admin.assignment.revoked")).count();
        assertThat(revokedEvents).isEqualTo(2);
        assertThat(outbox.eventTypes()).contains("admin.user.deactivated");
    }

    @Test
    void reactivate_fromInactive_succeeds() {
        User user = service.create(
                new CreateUserCommand("USR-1", "alice@example.com", "Alice", null, null, "admin"));
        service.deactivate(new DeactivateUserCommand(user.id(), false, "admin", false));
        User reactivated = service.reactivate(user.id(), "admin");
        assertThat(reactivated.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(outbox.eventTypes()).contains("admin.user.reactivated");
    }

    private void seedAssignment(UUID userId, UUID roleId) {
        assignmentRepo.save(UserRoleAssignment.grant(
                UUID.randomUUID(), userId, roleId, null,
                Instant.parse("2026-05-09T10:00:00Z"), "admin"));
    }
}
