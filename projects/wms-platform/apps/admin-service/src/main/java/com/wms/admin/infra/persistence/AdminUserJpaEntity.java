package com.wms.admin.infra.persistence;

import com.wms.admin.domain.User;
import com.wms.admin.domain.UserStatus;
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
@Table(name = "admin_user")
public class AdminUserJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_code", nullable = false, length = 40)
    private String userCode;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "default_warehouse_id")
    private UUID defaultWarehouseId;

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

    protected AdminUserJpaEntity() {
    }

    public static AdminUserJpaEntity fromDomain(User user) {
        AdminUserJpaEntity e = new AdminUserJpaEntity();
        e.id = user.id();
        e.userCode = user.userCode();
        e.email = user.email();
        e.name = user.name();
        e.phone = user.phone();
        e.status = user.status();
        e.defaultWarehouseId = user.defaultWarehouseId();
        e.version = user.version();
        e.createdAt = user.createdAt();
        e.createdBy = user.createdBy();
        e.updatedAt = user.updatedAt();
        e.updatedBy = user.updatedBy();
        return e;
    }

    public User toDomain() {
        return new User(id, userCode, email, name, phone, status, defaultWarehouseId, version,
                createdAt, createdBy, updatedAt, updatedBy);
    }

    public UUID getId() { return id; }
    public String getUserCode() { return userCode; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public UserStatus getStatus() { return status; }
    public UUID getDefaultWarehouseId() { return defaultWarehouseId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
