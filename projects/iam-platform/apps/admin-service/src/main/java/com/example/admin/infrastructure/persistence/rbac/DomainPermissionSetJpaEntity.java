package com.example.admin.infrastructure.persistence.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TASK-BE-631 / ADR-MONO-083 (Flyway V0052) — {@code domain_permission_set}: a named bundle of domain work roles.
 * {@code tenant_id NULL} ⟺ {@code system_key} set ⟺ a read-only system preset (CHECK in the schema).
 */
@Entity
@Table(name = "domain_permission_set")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainPermissionSetJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "set_id", length = 36, nullable = false, unique = true)
    private String setId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "system_key", length = 40)
    private String systemKey;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    /** A new TENANT set (never a preset — presets are seeded by V0052 only). */
    public static DomainPermissionSetJpaEntity createTenantSet(String setId, String tenantId, String name,
                                                               String description, Long createdBy, Instant now) {
        DomainPermissionSetJpaEntity e = new DomainPermissionSetJpaEntity();
        e.setId = setId;
        e.tenantId = tenantId;
        e.systemKey = null;
        e.name = name;
        e.description = description;
        e.createdBy = createdBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /** {@code null} leaves a field unchanged. Always bumps {@code updated_at}. */
    public void applyUpdate(String newName, String newDescription, Instant at) {
        if (newName != null) {
            this.name = newName;
        }
        if (newDescription != null) {
            this.description = newDescription;
        }
        this.updatedAt = at;
    }
}
