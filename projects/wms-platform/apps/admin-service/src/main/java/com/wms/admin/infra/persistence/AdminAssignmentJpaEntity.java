package com.wms.admin.infra.persistence;

import com.wms.admin.domain.AssignmentStatus;
import com.wms.admin.domain.UserRoleAssignment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_user_role_assignment")
public class AdminAssignmentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by", nullable = false, length = 120)
    private String grantedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 120)
    private String revokedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssignmentStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminAssignmentJpaEntity() {
    }

    public static AdminAssignmentJpaEntity fromDomain(UserRoleAssignment a) {
        AdminAssignmentJpaEntity e = new AdminAssignmentJpaEntity();
        e.id = a.id();
        e.userId = a.userId();
        e.roleId = a.roleId();
        e.warehouseId = a.warehouseId();
        e.grantedAt = a.grantedAt();
        e.grantedBy = a.grantedBy();
        e.revokedAt = a.revokedAt();
        e.revokedBy = a.revokedBy();
        e.status = a.status();
        e.version = a.version();
        e.createdAt = a.createdAt();
        e.updatedAt = a.updatedAt();
        return e;
    }

    public UserRoleAssignment toDomain() {
        return new UserRoleAssignment(id, userId, roleId, warehouseId, grantedAt, grantedBy,
                revokedAt, revokedBy, status, version, createdAt, updatedAt);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getRoleId() { return roleId; }
    public UUID getWarehouseId() { return warehouseId; }
    public Instant getGrantedAt() { return grantedAt; }
    public String getGrantedBy() { return grantedBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public AssignmentStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
