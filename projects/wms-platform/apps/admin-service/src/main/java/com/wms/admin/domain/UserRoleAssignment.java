package com.wms.admin.domain;

import com.wms.admin.domain.error.StateTransitionInvalidException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User ↔ Role binding aggregate. {@code warehouseId=null} = global scope.
 *
 * <p>Lifecycle: {@code ACTIVE → REVOKED} (terminal). Re-grant requires a new
 * row.
 *
 * <p>Uniqueness of {@code (userId, roleId, warehouseId)} among ACTIVE rows is
 * enforced by a partial unique index in V1__init.sql. The application layer
 * is responsible for the idempotent-grant return path (existing active row
 * surfaces as HTTP 200 rather than DUPLICATE_REQUEST).
 */
public final class UserRoleAssignment {

    private final UUID id;
    private final UUID userId;
    private final UUID roleId;
    private final UUID warehouseId;
    private final Instant grantedAt;
    private final String grantedBy;
    private final Instant revokedAt;
    private final String revokedBy;
    private final AssignmentStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public UserRoleAssignment(UUID id, UUID userId, UUID roleId, UUID warehouseId,
                              Instant grantedAt, String grantedBy,
                              Instant revokedAt, String revokedBy,
                              AssignmentStatus status, long version,
                              Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.roleId = Objects.requireNonNull(roleId, "roleId");
        this.warehouseId = warehouseId;
        this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt");
        this.grantedBy = Objects.requireNonNull(grantedBy, "grantedBy");
        this.revokedAt = revokedAt;
        this.revokedBy = revokedBy;
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static UserRoleAssignment grant(UUID id, UUID userId, UUID roleId, UUID warehouseId,
                                           Instant now, String grantedBy) {
        return new UserRoleAssignment(id, userId, roleId, warehouseId,
                now, grantedBy, null, null,
                AssignmentStatus.ACTIVE, 0L, now, now);
    }

    public UserRoleAssignment revoke(Instant now, String revokedBy) {
        if (status != AssignmentStatus.ACTIVE) {
            throw new StateTransitionInvalidException(
                    "assignment " + id + " is " + status + ", cannot revoke");
        }
        return new UserRoleAssignment(id, userId, roleId, warehouseId,
                grantedAt, grantedBy, now, revokedBy,
                AssignmentStatus.REVOKED, version, createdAt, now);
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public UUID roleId() { return roleId; }
    public UUID warehouseId() { return warehouseId; }
    public Instant grantedAt() { return grantedAt; }
    public String grantedBy() { return grantedBy; }
    public Instant revokedAt() { return revokedAt; }
    public String revokedBy() { return revokedBy; }
    public AssignmentStatus status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
