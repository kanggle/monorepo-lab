package com.wms.admin.application.assignment;

import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.repository.AssignmentRepository;
import com.wms.admin.application.repository.OutboxRepository;
import com.wms.admin.domain.UserRoleAssignment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared helper for cascade-revoking active {@link UserRoleAssignment}s and
 * appending the {@code admin.assignment.revoked} outbox event. Extracted from
 * {@link com.wms.admin.application.user.UserService} and
 * {@link com.wms.admin.application.role.RoleService} to eliminate duplication
 * (TASK-BE-297).
 *
 * <p>This component depends only on {@link AssignmentRepository},
 * {@link OutboxRepository} and {@link AdminEventEnvelopeBuilder} — no
 * dependency on UserService or RoleService, so there is no circular-dependency
 * risk.
 */
@Component
public class AssignmentEventHelper {

    private static final String AGGREGATE_TYPE_ASSIGNMENT = "assignment";

    private final AssignmentRepository assignmentRepository;
    private final OutboxRepository outboxRepository;
    private final AdminEventEnvelopeBuilder envelopeBuilder;

    public AssignmentEventHelper(AssignmentRepository assignmentRepository,
                                 OutboxRepository outboxRepository,
                                 AdminEventEnvelopeBuilder envelopeBuilder) {
        this.assignmentRepository = assignmentRepository;
        this.outboxRepository = outboxRepository;
        this.envelopeBuilder = envelopeBuilder;
    }

    /**
     * Cascade-revokes every active assignment in {@code active}: saves each
     * revoked assignment via {@link AssignmentRepository#save(UserRoleAssignment)}
     * and appends an {@code admin.assignment.revoked} outbox event for each.
     *
     * <p>The {@code occurredAt} instant MUST be supplied by the caller (typically
     * a single {@code clock.instant()} call) so every cascade event in this loop
     * shares the same timestamp. The caller's subsequent deactivate-event instant
     * is intentionally distinct — this helper preserves that two-phase clock
     * pattern.
     *
     * @param active        the active assignments to cascade-revoke
     * @param cascadeReason e.g. {@code "USER_DEACTIVATED"} or {@code "ROLE_DEACTIVATED"}
     * @param actorId       the actor performing the cascade revocation
     * @param occurredAt    the event timestamp shared across the cascade loop
     * @return the ids of the saved revoked assignments, in iteration order
     */
    public List<UUID> cascadeRevoke(List<UserRoleAssignment> active, String cascadeReason,
                                    String actorId, Instant occurredAt) {
        List<UUID> revokedIds = new ArrayList<>();
        for (UserRoleAssignment a : active) {
            UserRoleAssignment revoked = a.revoke(occurredAt, actorId);
            UserRoleAssignment saved = assignmentRepository.save(revoked);
            revokedIds.add(saved.id());
            appendAssignmentRevokedEvent(saved, cascadeReason, actorId, occurredAt);
        }
        return revokedIds;
    }

    /**
     * Builds the {@code admin.assignment.revoked} payload and appends it to the
     * outbox inside the caller's active transaction.
     *
     * @param a             the already-revoked assignment (revokedAt / revokedBy must be set)
     * @param cascadeReason e.g. {@code "USER_DEACTIVATED"} or {@code "ROLE_DEACTIVATED"}
     * @param actorId       the actor performing the cascade revocation
     * @param occurredAt    the event timestamp (same instant used for the parent operation)
     */
    public void appendAssignmentRevokedEvent(UserRoleAssignment a, String cascadeReason,
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
        outboxRepository.append(AGGREGATE_TYPE_ASSIGNMENT, a.id().toString(),
                "admin.assignment.revoked", envelope, a.id().toString());
    }
}
