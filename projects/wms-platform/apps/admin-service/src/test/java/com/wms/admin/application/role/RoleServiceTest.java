package com.wms.admin.application.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.assignment.AssignmentEventHelper;
import com.wms.admin.application.fakes.InMemoryAssignmentRepository;
import com.wms.admin.application.fakes.InMemoryRoleRepository;
import com.wms.admin.application.fakes.RecordingOutboxPort;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import com.wms.admin.domain.UserRoleAssignment;
import com.wms.admin.domain.error.RoleBuiltinImmutableException;
import com.wms.admin.domain.error.RoleCodeDuplicateException;
import com.wms.admin.domain.error.RoleInUseException;
import com.wms.admin.domain.error.SettingValidationErrorException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleServiceTest {

    private InMemoryRoleRepository roleRepo;
    private InMemoryAssignmentRepository assignmentRepo;
    private RecordingOutboxPort outbox;
    private ObjectMapper mapper;
    private RoleService service;

    @BeforeEach
    void setUp() {
        roleRepo = new InMemoryRoleRepository();
        assignmentRepo = new InMemoryAssignmentRepository();
        outbox = new RecordingOutboxPort();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(Instant.parse("2026-05-09T10:00:00Z"), ZoneOffset.UTC);
        AdminEventEnvelopeBuilder envelopeBuilder = new AdminEventEnvelopeBuilder(mapper);
        service = new RoleService(roleRepo, assignmentRepo, outbox,
                envelopeBuilder, new AssignmentEventHelper(assignmentRepo, outbox, envelopeBuilder), mapper, fixed);
    }

    @Test
    void create_happyPath_savesRoleAndEmitsCreatedEvent() {
        Role saved = service.create(new CreateRoleCommand(
                "WMS_SHIFT_LEAD", "Shift Lead", "desc",
                List.of("INVENTORY_READ", "ALERT_ACKNOWLEDGE"), "admin"));
        assertThat(saved.roleCode()).isEqualTo("WMS_SHIFT_LEAD");
        assertThat(saved.status()).isEqualTo(RoleStatus.ACTIVE);
        assertThat(saved.isBuiltin()).isFalse();
        assertThat(outbox.eventTypes()).containsExactly("admin.role.created");
    }

    @Test
    void create_unknownPermission_raisesValidationError() {
        assertThatThrownBy(() -> service.create(new CreateRoleCommand(
                "WMS_X", "X", null, List.of("FOO_BAR_UNKNOWN"), "admin")))
                .isInstanceOf(SettingValidationErrorException.class);
    }

    @Test
    void create_emptyPermissions_raisesValidationError() {
        assertThatThrownBy(() -> service.create(new CreateRoleCommand(
                "WMS_X", "X", null, List.of(), "admin")))
                .isInstanceOf(SettingValidationErrorException.class);
    }

    @Test
    void create_duplicateCode_raisesRoleCodeDuplicate() {
        service.create(new CreateRoleCommand(
                "WMS_X", "X", null, List.of("INVENTORY_READ"), "admin"));
        assertThatThrownBy(() -> service.create(new CreateRoleCommand(
                "WMS_X", "Y", null, List.of("INVENTORY_READ"), "admin")))
                .isInstanceOf(RoleCodeDuplicateException.class);
    }

    @Test
    void deactivate_builtin_raisesBuiltinImmutable() {
        Role builtin = new Role(UUID.randomUUID(), "WMS_ADMIN", "Admin", null,
                "[\"INVENTORY_READ\"]", RoleStatus.ACTIVE, true, 0L,
                Instant.now(), "system", Instant.now(), "system");
        roleRepo.seed(builtin);
        assertThatThrownBy(() -> service.deactivate(
                new DeactivateRoleCommand(builtin.id(), false, "admin", true)))
                .isInstanceOf(RoleBuiltinImmutableException.class);
    }

    @Test
    void deactivate_inUseAndForceFalse_raisesRoleInUse() {
        Role role = service.create(new CreateRoleCommand(
                "WMS_X", "X", null, List.of("INVENTORY_READ"), "admin"));
        UUID userId = UUID.randomUUID();
        assignmentRepo.save(UserRoleAssignment.grant(
                UUID.randomUUID(), userId, role.id(), null, Instant.now(), "admin"));
        assertThatThrownBy(() -> service.deactivate(
                new DeactivateRoleCommand(role.id(), false, "admin", false)))
                .isInstanceOf(RoleInUseException.class);
    }

    @Test
    void deactivate_forceCascade_revokesAssignmentsAndEmitsEvents() {
        Role role = service.create(new CreateRoleCommand(
                "WMS_X", "X", null, List.of("INVENTORY_READ"), "admin"));
        assignmentRepo.save(UserRoleAssignment.grant(
                UUID.randomUUID(), UUID.randomUUID(), role.id(), null, Instant.now(), "admin"));
        DeactivateRoleResult result = service.deactivate(
                new DeactivateRoleCommand(role.id(), true, "superadmin", true));
        assertThat(result.role().status()).isEqualTo(RoleStatus.INACTIVE);
        assertThat(result.revokedAssignmentIds()).hasSize(1);
        assertThat(outbox.eventTypes()).contains("admin.assignment.revoked", "admin.role.deactivated");
    }

    @Test
    void update_builtin_onlyPermissionsChange() {
        Role builtin = new Role(UUID.randomUUID(), "WMS_ADMIN", "Admin", "orig-desc",
                "[\"INVENTORY_READ\"]", RoleStatus.ACTIVE, true, 0L,
                Instant.now(), "system", Instant.now(), "system");
        roleRepo.seed(builtin);
        Role updated = service.update(new UpdateRoleCommand(
                builtin.id(), "NewName", "NewDesc", List.of("INVENTORY_WRITE"), "admin"));
        // built-in: name/description are silently dropped, permissions changed.
        assertThat(updated.name()).isEqualTo("Admin");
        assertThat(updated.description()).isEqualTo("orig-desc");
        assertThat(updated.permissionsJson()).contains("INVENTORY_WRITE");
        assertThat(outbox.eventTypes()).contains("admin.role.updated");
    }
}
