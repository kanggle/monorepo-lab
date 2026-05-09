package com.wms.admin.application.assignment;

import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.repository.OutboxRepository;
import com.wms.admin.domain.UserRoleAssignment;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Shared helper that builds and appends the {@code admin.assignment.revoked}
 * outbox event. Extracted from {@link com.wms.admin.application.user.UserService}
 * and {@link com.wms.admin.application.role.RoleService} to eliminate duplication.
 *
 * <p>This component has no dependency on UserService or RoleService — only on
 * {@link OutboxRepository} and {@link AdminEventEnvelopeBuilder} — so there is
 * no circular-dependency risk.
 */
@Component
public class AssignmentEventHelper {

    private static final String AGGREGATE_TYPE_ASSIGNMENT = "assignment";

    private final OutboxRepository outboxRepository;
    private final AdminEventEnvelopeBuilder envelopeBuilder;

    public AssignmentEventHelper(OutboxRepository outboxRepository,
                                 AdminEventEnvelopeBuilder envelopeBuilder) {
        this.outboxRepository = outboxRepository;
        this.envelopeBuilder = envelopeBuilder;
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
