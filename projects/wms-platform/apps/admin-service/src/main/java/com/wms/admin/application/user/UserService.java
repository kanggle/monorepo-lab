package com.wms.admin.application.user;

import com.example.common.id.UuidV7;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.port.AssignmentRepository;
import com.wms.admin.application.port.OutboxPort;
import com.wms.admin.application.port.UserRepository;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserRoleAssignment;
import com.wms.admin.domain.UserStatus;
import com.wms.admin.domain.error.UserEmailDuplicateException;
import com.wms.admin.domain.error.UserHasActiveAssignmentsException;
import com.wms.admin.domain.error.UserNotFoundException;
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
 * User aggregate use-cases. Application-layer authz enforcement (per
 * architecture.md § Security): {@code @PreAuthorize} on the use-case method
 * + a fine-grained {@code force=true ⇒ WMS_SUPERADMIN} check inside
 * {@link #deactivate(DeactivateUserCommand)}.
 *
 * <p>Outbox writes (T3) sit in the same {@code @Transactional} boundary as
 * the aggregate save. Force-deactivate cascades emit one
 * {@code admin.assignment.revoked} per active assignment + one
 * {@code admin.user.deactivated}.
 */
@Service
public class UserService {

    private static final String AGGREGATE_TYPE = "user";
    private static final String AGGREGATE_TYPE_ASSIGNMENT = "assignment";

    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final OutboxPort outboxPort;
    private final AdminEventEnvelopeBuilder envelopeBuilder;
    private final Clock clock;

    public UserService(UserRepository userRepository,
                       AssignmentRepository assignmentRepository,
                       OutboxPort outboxPort,
                       AdminEventEnvelopeBuilder envelopeBuilder,
                       Clock clock) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxPort = outboxPort;
        this.envelopeBuilder = envelopeBuilder;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public User create(CreateUserCommand cmd) {
        String email = normaliseEmail(cmd.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserEmailDuplicateException(email);
        }
        Instant now = clock.instant();
        User user = User.create(UuidV7.randomUuid(), cmd.userCode(), email,
                cmd.name(), cmd.phone(), cmd.defaultWarehouseId(), now, cmd.actorId());
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Race window: unique index caught what existsBy missed.
            throw new UserEmailDuplicateException(email);
        }
        appendCreatedEvent(saved, cmd.actorId(), now);
        return saved;
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public User update(UpdateUserCommand cmd) {
        User existing = userRepository.findById(cmd.id())
                .orElseThrow(() -> new UserNotFoundException(cmd.id()));
        String newEmail = cmd.email() != null ? normaliseEmail(cmd.email()) : null;
        if (newEmail != null && !newEmail.equalsIgnoreCase(existing.email())
                && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new UserEmailDuplicateException(newEmail);
        }
        Instant now = clock.instant();
        User updated = existing.updateProfile(cmd.name(), newEmail, cmd.phone(),
                cmd.defaultWarehouseId(), now, cmd.actorId());
        User saved;
        try {
            saved = userRepository.save(updated);
        } catch (DataIntegrityViolationException e) {
            throw new UserEmailDuplicateException(newEmail != null ? newEmail : existing.email());
        }
        List<String> changedFields = computeChangedFields(existing, saved);
        if (!changedFields.isEmpty()) {
            appendUpdatedEvent(saved, changedFields, cmd.actorId(), now);
        }
        return saved;
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public DeactivateUserResult deactivate(DeactivateUserCommand cmd) {
        User existing = userRepository.findById(cmd.id())
                .orElseThrow(() -> new UserNotFoundException(cmd.id()));
        int activeAssignments = assignmentRepository.countActiveByUserId(existing.id());

        List<UUID> revokedIds = new ArrayList<>();
        if (activeAssignments > 0) {
            if (!cmd.force()) {
                throw new UserHasActiveAssignmentsException(activeAssignments);
            }
            if (!cmd.callerIsSuperadmin()) {
                // force=true requires WMS_SUPERADMIN
                throw new AccessDeniedException("force=true requires WMS_SUPERADMIN role");
            }
            Instant now = clock.instant();
            List<UserRoleAssignment> active = assignmentRepository.findActiveByUserId(existing.id());
            for (UserRoleAssignment a : active) {
                UserRoleAssignment revoked = a.revoke(now, cmd.actorId());
                UserRoleAssignment saved = assignmentRepository.save(revoked);
                revokedIds.add(saved.id());
                appendAssignmentRevokedEvent(saved, "USER_DEACTIVATED", cmd.actorId(), now);
            }
        }
        Instant now = clock.instant();
        User deactivated = existing.deactivate(now, cmd.actorId());
        User saved = userRepository.save(deactivated);
        appendDeactivatedEvent(saved, cmd.force(), revokedIds, cmd.actorId(), now);
        return new DeactivateUserResult(saved, revokedIds);
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public User reactivate(UUID id, String actorId) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        Instant now = clock.instant();
        User reactivated = existing.reactivate(now, actorId);
        User saved = userRepository.save(reactivated);
        appendReactivatedEvent(saved, actorId, now);
        return saved;
    }

    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<User> search(UserStatus status, UUID warehouseId, String q, Pageable pageable) {
        return userRepository.search(status, warehouseId, q, pageable);
    }

    private static String normaliseEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }

    private static List<String> computeChangedFields(User before, User after) {
        List<String> fields = new ArrayList<>();
        if (!equalsNullable(before.name(), after.name())) fields.add("name");
        if (!equalsNullable(before.email(), after.email())) fields.add("email");
        if (!equalsNullable(before.phone(), after.phone())) fields.add("phone");
        if (!equalsNullable(before.defaultWarehouseId(), after.defaultWarehouseId())) fields.add("defaultWarehouseId");
        return fields;
    }

    private static boolean equalsNullable(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    // ----- Outbox event helpers ----------------------------------------------

    private void appendCreatedEvent(User user, String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.id().toString());
        payload.put("userCode", user.userCode());
        payload.put("email", user.email());
        payload.put("name", user.name());
        payload.put("phone", user.phone());
        payload.put("defaultWarehouseId", user.defaultWarehouseId() != null ? user.defaultWarehouseId().toString() : null);
        payload.put("status", user.status().name());
        appendUserEvent("admin.user.created", user.id(), payload, actorId, occurredAt);
    }

    private void appendUpdatedEvent(User user, List<String> changedFields, String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.id().toString());
        payload.put("changedFields", changedFields);
        Map<String, Object> userBlock = new LinkedHashMap<>();
        userBlock.put("userCode", user.userCode());
        userBlock.put("email", user.email());
        userBlock.put("name", user.name());
        userBlock.put("phone", user.phone());
        userBlock.put("defaultWarehouseId", user.defaultWarehouseId() != null ? user.defaultWarehouseId().toString() : null);
        userBlock.put("status", user.status().name());
        payload.put("user", userBlock);
        appendUserEvent("admin.user.updated", user.id(), payload, actorId, occurredAt);
    }

    private void appendDeactivatedEvent(User user, boolean force, List<UUID> revokedIds,
                                        String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.id().toString());
        payload.put("userCode", user.userCode());
        payload.put("force", force);
        payload.put("cascadeRevokedAssignmentIds",
                revokedIds.stream().map(UUID::toString).toList());
        appendUserEvent("admin.user.deactivated", user.id(), payload, actorId, occurredAt);
    }

    private void appendReactivatedEvent(User user, String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.id().toString());
        payload.put("userCode", user.userCode());
        appendUserEvent("admin.user.reactivated", user.id(), payload, actorId, occurredAt);
    }

    private void appendUserEvent(String eventType, UUID userId, Map<String, Object> payload,
                                 String actorId, Instant occurredAt) {
        String envelope = envelopeBuilder.build(
                eventType, AGGREGATE_TYPE, userId.toString(),
                actorId, occurredAt, payload);
        outboxPort.append(AGGREGATE_TYPE, userId.toString(), eventType, envelope, userId.toString());
    }

    private void appendAssignmentRevokedEvent(UserRoleAssignment a, String cascadeReason,
                                              String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", a.id().toString());
        payload.put("userId", a.userId().toString());
        payload.put("roleId", a.roleId().toString());
        payload.put("warehouseId", a.warehouseId() != null ? a.warehouseId().toString() : null);
        payload.put("revokedAt", a.revokedAt() != null ? a.revokedAt().toString() : null);
        payload.put("revokedBy", a.revokedBy());
        payload.put("cascadeReason", cascadeReason);
        String envelope = envelopeBuilder.build(
                "admin.assignment.revoked", AGGREGATE_TYPE_ASSIGNMENT, a.id().toString(),
                actorId, occurredAt, payload);
        outboxPort.append(AGGREGATE_TYPE_ASSIGNMENT, a.id().toString(), "admin.assignment.revoked",
                envelope, a.id().toString());
    }
}
