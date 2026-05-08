package com.wms.admin.infra.persistence;

import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_role")
public class AdminRoleJpaEntity {

    @Id
    private UUID id;

    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions_json", nullable = false, columnDefinition = "jsonb")
    private String permissionsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoleStatus status;

    @Column(name = "is_builtin", nullable = false)
    private boolean isBuiltin;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected AdminRoleJpaEntity() {
    }

    public static AdminRoleJpaEntity fromDomain(Role role) {
        AdminRoleJpaEntity e = new AdminRoleJpaEntity();
        e.id = role.id();
        e.roleCode = role.roleCode();
        e.name = role.name();
        e.description = role.description();
        e.permissionsJson = role.permissionsJson();
        e.status = role.status();
        e.isBuiltin = role.isBuiltin();
        e.version = role.version();
        e.createdAt = role.createdAt();
        e.createdBy = role.createdBy();
        e.updatedAt = role.updatedAt();
        e.updatedBy = role.updatedBy();
        return e;
    }

    public Role toDomain() {
        return new Role(id, roleCode, name, description, permissionsJson, status, isBuiltin,
                version, createdAt, createdBy, updatedAt, updatedBy);
    }

    public UUID getId() { return id; }
    public String getRoleCode() { return roleCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getPermissionsJson() { return permissionsJson; }
    public RoleStatus getStatus() { return status; }
    public boolean isBuiltin() { return isBuiltin; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
