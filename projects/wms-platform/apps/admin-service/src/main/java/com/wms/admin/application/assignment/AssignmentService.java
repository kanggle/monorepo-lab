package com.wms.admin.application.assignment;

import com.example.common.id.UuidV7;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.port.AssignmentRepository;
import com.wms.admin.application.port.OutboxPort;
import com.wms.admin.application.port.RoleRepository;
import com.wms.admin.application.port.UserRepository;
import com.wms.admin.domain.AssignmentStatus;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserRoleAssignment;
import com.wms.admin.domain.UserStatus;
import com.wms.admin.domain.error.AssignmentNotFoundException;
import com.wms.admin.domain.error.RoleNotFoundException;
import com.wms.admin.domain.error.StateTransitionInvalidException;
import com.wms.admin.domain.error.UserNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assignment grant + revoke. Grant is idempotent — if {@code (userId, roleId,
 * warehouseId)} is already ACTIVE, returns the existing row + {@code created=false}
 * so the controller can return HTTP 200 instead of 201.
 *
 * <p>Both User and Role must be ACTIVE at grant time
 * ({@link StateTransitionInvalidException} otherwise). Revocation is terminal.
 */
@Service
public class AssignmentService {

    private static final String AGGREGATE_TYPE = "assignment";

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OutboxPort outboxPort;
    private final AdminEventEnvelopeBuilder envelopeBuilder;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             UserRepository userRepository,
                             RoleRepository roleRepository,
                             OutboxPort outboxPort,
                             AdminEventEnvelopeBuilder envelopeBuilder,
                             Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.outboxPort = outboxPort;
        this.envelopeBuilder = envelopeBuilder;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public GrantAssignmentResult grant(GrantAssignmentCommand cmd) {
        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new UserNotFoundException(cmd.userId()));
        Role role = roleRepository.findById(cmd.roleId())
                .orElseThrow(() -> new RoleNotFoundException(cmd.roleId()));
        if (user.status() != UserStatus.ACTIVE) {
            throw new StateTransitionInvalidException("user " + user.id() + " is not ACTIVE");
        }
        if (role.status() != RoleStatus.ACTIVE) {
            throw new StateTransitionInvalidException("role " + role.id() + " is not ACTIVE");
        }

        Optional<UserRoleAssignment> existing =
                assignmentRepository.findActiveTriple(cmd.userId(), cmd.roleId(), cmd.warehouseId());
        if (existing.isPresent()) {
            return new GrantAssignmentResult(existing.get(), false);
        }

        Instant now = clock.instant();
        UserRoleAssignment a = UserRoleAssignment.grant(
                UuidV7.randomUuid(), cmd.userId(), cmd.roleId(), cmd.warehouseId(),
                now, cmd.actorId());
        UserRoleAssignment saved = assignmentRepository.save(a);
        appendGrantedEvent(saved, user, role, cmd.actorId(), now);
        return new GrantAssignmentResult(saved, true);
    }

    @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")
    @Transactional
    public UserRoleAssignment revoke(UUID id, String actorId) {
        UserRoleAssignment existing = assignmentRepository.findById(id)
                .orElseThrow(() -> new AssignmentNotFoundException(id));
        Instant now = clock.instant();
        UserRoleAssignment revoked = existing.revoke(now, actorId);
        UserRoleAssignment saved = assignmentRepository.save(revoked);
        appendRevokedEvent(saved, actorId, now);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<UserRoleAssignment> search(UUID userId, UUID roleId, UUID warehouseId,
                                           AssignmentStatus status, Pageable pageable) {
        return assignmentRepository.search(userId, roleId, warehouseId, status, pageable);
    }

    private void appendGrantedEvent(UserRoleAssignment a, User user, Role role,
                                    String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", a.id().toString());
        payload.put("userId", a.userId().toString());
        payload.put("userCode", user.userCode());
        payload.put("roleId", a.roleId().toString());
        payload.put("roleCode", role.roleCode());
        payload.put("warehouseId", a.warehouseId() != null ? a.warehouseId().toString() : null);
        payload.put("grantedAt", a.grantedAt().toString());
        payload.put("grantedBy", a.grantedBy());
        String envelope = envelopeBuilder.build(
                "admin.assignment.granted", AGGREGATE_TYPE, a.id().toString(),
                actorId, occurredAt, payload);
        outboxPort.append(AGGREGATE_TYPE, a.id().toString(), "admin.assignment.granted",
                envelope, a.id().toString());
    }

    private void appendRevokedEvent(UserRoleAssignment a, String actorId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", a.id().toString());
        payload.put("userId", a.userId().toString());
        payload.put("roleId", a.roleId().toString());
        payload.put("warehouseId", a.warehouseId() != null ? a.warehouseId().toString() : null);
        payload.put("revokedAt", a.revokedAt() != null ? a.revokedAt().toString() : null);
        payload.put("revokedBy", a.revokedBy());
        payload.put("cascadeReason", null);
        String envelope = envelopeBuilder.build(
                "admin.assignment.revoked", AGGREGATE_TYPE, a.id().toString(),
                actorId, occurredAt, payload);
        outboxPort.append(AGGREGATE_TYPE, a.id().toString(), "admin.assignment.revoked",
                envelope, a.id().toString());
    }
}
