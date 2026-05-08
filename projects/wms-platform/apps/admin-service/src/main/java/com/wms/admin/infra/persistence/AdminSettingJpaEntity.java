package com.wms.admin.infra.persistence;

import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for {@code admin_setting}. Composite PK {@code (key, warehouse_id)}.
 * GLOBAL-scoped rows carry the sentinel UUID {@link AdminSettingId#NULL_SENTINEL}
 * in warehouse_id; the adapter translates it to {@code null} when surfacing to
 * the domain layer.
 *
 * <p>JSONB columns: {@code value_json}, {@code schema_json} —
 * {@code @JdbcTypeCode(SqlTypes.JSON)} required (TASK-SCM-INT-001b root cause #2).
 */
@Entity
@Table(name = "admin_setting")
@IdClass(AdminSettingId.class)
public class AdminSettingJpaEntity {

    @Id
    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @Id
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettingScope scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json", nullable = false, columnDefinition = "jsonb")
    private String valueJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false, columnDefinition = "jsonb")
    private String schemaJson;

    @Column(length = 500)
    private String description;

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

    protected AdminSettingJpaEntity() {
    }

    public static AdminSettingJpaEntity fromDomain(Setting s) {
        AdminSettingJpaEntity e = new AdminSettingJpaEntity();
        e.key = s.key();
        e.warehouseId = s.warehouseId() == null ? AdminSettingId.NULL_SENTINEL : s.warehouseId();
        e.scope = s.scope();
        e.valueJson = s.valueJson();
        e.schemaJson = s.schemaJson();
        e.description = s.description();
        e.version = s.version();
        e.createdAt = s.createdAt();
        e.createdBy = s.createdBy();
        e.updatedAt = s.updatedAt();
        e.updatedBy = s.updatedBy();
        return e;
    }

    public Setting toDomain() {
        UUID exposedWarehouseId = AdminSettingId.NULL_SENTINEL.equals(warehouseId) ? null : warehouseId;
        return new Setting(key, scope, exposedWarehouseId, valueJson, schemaJson, description,
                version, createdAt, createdBy, updatedAt, updatedBy);
    }

    public String getKey() { return key; }
    public UUID getWarehouseId() { return warehouseId; }
    public SettingScope getScope() { return scope; }
    public String getValueJson() { return valueJson; }
    public String getSchemaJson() { return schemaJson; }
    public String getDescription() { return description; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
