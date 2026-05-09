package com.wms.admin.application.role;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.DomainUtils;
import com.wms.admin.application.PermissionCatalog;
import com.wms.admin.application.assignment.AssignmentEventHelper;
import com.wms.admin.application.repository.AssignmentRepository;
import com.wms.admin.application.repository.OutboxRepository;
import com.wms.admin.application.repository.RoleRepository;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import com.wms.admin.domain.UserRoleAssignment;
import com.wms.admin.domain.error.RoleBuiltinImmutableException;
import com.wms.admin.domain.error.RoleCodeDuplicateException;
import com.wms.admin.domain.error.RoleInUseException;
import com.wms.admin.domain.error.RoleNotFoundException;
import com.wms.admin.domain.error.SettingValidationErrorException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role aggregate use-cases. Same authz pattern as {@link com.wms.admin.application.user.UserService}.
 *
 * <p>Built-in roles ({@code WMS_VIEWER} / {@code WMS_OPERATOR} /
 * {@code WMS_ADMIN} / {@code WMS_SUPERADMIN}): only {@code permissionsJson}
 * may be PATCHed; {@code name} / {@code description} updates are silently
 * ignored. Deactivation is rejected with {@link RoleBuiltinImmutableException}.
 */
@Service
public class RoleService {

    private static final String AGGREGATE_TYPE = "role";

    private final RoleRepository roleRepository;
    private final AssignmentRepository assignmentRepository;
    private final OutboxRepository outboxRepository;
    private final AdminEventEnvelopeBuilder envelopeBuilder;
    private final AssignmentEventHelper assignmentEventHelper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RoleService(RoleRepository roleRepository,
                       AssignmentRepository assignmentRepository,
                       OutboxRepository outboxRepository,
                       AdminEventEnvelopeBuilder envelopeBuilder,
                       AssignmentEventHelper assignmentEventHelper,
                       ObjectMapper objectMapper,
                       Clock clock) {
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxRepository = outboxRepository;
        this.envelopeBuilder = envelopeBuilder;
        this.assignmentEventHelper = assignmentEventHelper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public Role create(CreateRoleCommand cmd) {
        validatePermissions(cmd.permissions());
        if (roleRepository.existsByRoleCode(cmd.roleCode())) {
            throw new RoleCodeDuplicateException(cmd.roleCode());
        }
        Instant now = clock.instant();
        String permissionsJson = serialisePermissions(cmd.permissions());
        Role role = Role.create(UuidV7.randomUuid(), cmd.roleCode(), cmd.name(), cmd.description(),
                permissionsJson, now, cmd.actorId());
        Role saved;
        try {
            saved = roleRepository.save(role);
        } catch (DataIntegrityViolationException e) {
            throw new RoleCodeDuplicateException(cmd.roleCode());
        }
        appendCreatedEvent(saved, cmd.actorId(), now);
        return saved;
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public Role update(UpdateRoleCommand cmd) {
        Role existing = roleRepository.findById(cmd.id())
                .orElseThrow(() -> new RoleNotFoundException(cmd.id()));

        String newPermissionsJson = null;
        if (cmd.permissions() != null) {
            validatePermissions(cmd.permissions());
            newPermissionsJson = serialisePermissions(cmd.permissions());
        }
        // Built-in: only permissionsJson may change. Drop name/description silently.
        String newName = existing.isBuiltin() ? null : cmd.name();
        String newDescription = existing.isBuiltin() ? null : cmd.description();

        Instant now = clock.instant();
        Role updated = existing.updateMetadata(newName, newDescription, newPermissionsJson, now, cmd.actorId());
        Role saved = roleRepository.save(updated);

        List<String> changedFields = computeChangedFields(existing, saved);
        if (!changedFields.isEmpty()) {
            appendUpdatedEvent(saved, changedFields, cmd.actorId(), now);
        }
        return saved;
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public DeactivateRoleResult deactivate(DeactivateRoleCommand cmd) {
        Role existing = roleRepository.findById(cmd.id())
                .orElseThrow(() -> new RoleNotFoundException(cmd.id()));
        if (existing.isBuiltin()) {
            throw new RoleBuiltinImmutableException(existing.roleCode());
        }
        int activeAssignments = assignmentRepository.countActiveByRoleId(existing.id());
        List<UUID> revokedIds = new ArrayList<>();
        if (activeAssignments > 0) {
            if (!cmd.force()) {
                throw new RoleInUseException(activeAssignments);
            }
            if (!cmd.callerIsSuperadmin()) {
                throw new AccessDeniedException("force=true requires WMS_SUPERADMIN role");
            }
            Instant now = clock.instant();
            List<UserRoleAssignment> active = assignmentRepository.findActiveByRoleId(existing.id());
            for (UserRoleAssignment a : active) {
                UserRoleAssignment revoked = a.revoke(now, cmd.actorId());
                UserRoleAssignment saved = assignmentRepository.save(revoked);
                revokedIds.add(saved.id());
                assignmentEventHelper.appendAssignmentRevokedEvent(saved, "ROLE_DEACTIVATED", cmd.actorId(), now);
            }
        }
        Instant now = clock.instant();
        Role deactivated = existing.deactivate(now, cmd.actorId());
        Role saved = roleRepository.save(deactivated);
        appendDeactivatedEvent(saved, cmd.force(), revokedIds, cmd.actorId(), now);
        return new DeactivateRoleResult(saved, revokedIds);
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public Role reactivate(UUID id, String actorId) {
        Role existing = roleRepository.findById(id)
                .orElseThrow(() -> new RoleNotFoundException(id));
        Instant now = clock.instant();
        Role reactivated = existing.reactivate(now, actorId);
        Role saved = roleRepository.save(reactivated);
        appendReactivatedEvent(saved, actorId, now);
        return saved;
    }

    @Transactional(readOnly = true)
    public Role findById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RoleNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Role> search(RoleStatus status, Pageable pageable) {
        return roleRepository.search(status, pageable);
    }

    private static void validatePermissions(List<String> perms) {
        if (perms == null || perms.isEmpty()) {
            throw new SettingValidationErrorException("permissions must be non-empty");
        }
        for (String p : perms) {
            if (p == null || p.isBlank()) {
                throw new SettingValidationErrorException("permission entries must be non-blank");
            }
            if (!PermissionCatalog.isKnown(p)) {
                throw new SettingValidationErrorException("unknown permission: " + p);
            }
        }
    }

    private String serialisePermissions(List<String> perms) {
        try {
            return objectMapper.writeValueAsString(perms);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise permissions", e);
        }
    }

    private List<String> deserialisePermissions(String permissionsJson) {
        try {
            return objectMapper.readValue(permissionsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> computeChangedFields(Role before, Role after) {
        List<String> fields = new ArrayList<>();
        if (!DomainUtils.equalsNullable(before.name(), after.name())) fields.add("name");
        if (!DomainUtils.equalsNullable(before.description(), after.description())) fields.add("description");
        if (!DomainUtils.equalsNullable(before.permissionsJson(), after.permissionsJson())) fields.add("permissionsJson");
        return fields;
    }

    // ----- Outbox event helpers ----------------------------------------------

    private void appendCreatedEvent(Role role, String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roleId", role.id().toString());
        payload.put("roleCode", role.roleCode());
        payload.put("name", role.name());
        payload.put("description", role.description());
        payload.put("permissionsJson", deserialisePermissions(role.permissionsJson()));
        payload.put("status", role.status().name());
        appendRoleEvent("admin.role.created", role.id(), payload, actorId, occurredAt);
    }

    private void appendUpdatedEvent(Role role, List<String> changedFields, String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roleId", role.id().toString());
        payload.put("roleCode", role.roleCode());
        payload.put("changedFields", changedFields);
        Map<String, Object> roleBlock = new LinkedHashMap<>();
        roleBlock.put("name", role.name());
        roleBlock.put("description", role.description());
        roleBlock.put("permissionsJson", deserialisePermissions(role.permissionsJson()));
        roleBlock.put("status", role.status().name());
        payload.put("role", roleBlock);
        appendRoleEvent("admin.role.updated", role.id(), payload, actorId, occurredAt);
    }

    private void appendDeactivatedEvent(Role role, boolean force, List<UUID> revokedIds,
                                        String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roleId", role.id().toString());
        payload.put("roleCode", role.roleCode());
        payload.put("force", force);
        payload.put("cascadeRevokedAssignmentIds",
                revokedIds.stream().map(UUID::toString).toList());
        appendRoleEvent("admin.role.deactivated", role.id(), payload, actorId, occurredAt);
    }

    private void appendReactivatedEvent(Role role, String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roleId", role.id().toString());
        payload.put("roleCode", role.roleCode());
        appendRoleEvent("admin.role.reactivated", role.id(), payload, actorId, occurredAt);
    }

    private void appendRoleEvent(String eventType, UUID roleId, Map<String, Object> payload,
                                 String actorId, Instant occurredAt) {
        String envelope = envelopeBuilder.build(
                eventType, AGGREGATE_TYPE, roleId.toString(),
                actorId, occurredAt, payload);
        outboxRepository.append(AGGREGATE_TYPE, roleId.toString(), eventType, envelope, roleId.toString());
    }

}
