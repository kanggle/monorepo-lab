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
 * TASK-BE-520 / ADR-MONO-046 D1/D3 (Flyway V0043) — JPA entity for {@code operator_group}:
 * the tenant-scoped named unit of {@code admin_operators} that roles / tenant-assignments
 * are fanned out to (AWS IAM User Group / Google Group parity). The external
 * {@code group_id} (UUID v7) is what HTTP paths carry; the internal BIGINT {@code id} is
 * what the {@code group_origin} fan-out marker references.
 *
 * <p>{@code tenant_id} is a single concrete tenant ({@code '*'} forbidden — CHECK + app),
 * the {@code TenantScopeGuard} target (D3).
 */
@Entity
@Table(name = "operator_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorGroupJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", length = 36, nullable = false, unique = true)
    private String groupId;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

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

    /** Factory for a fresh group ({@code memberCount=0}, {@code grantCount=0}). */
    public static OperatorGroupJpaEntity create(String groupId, String tenantId, String name,
                                                String description, Long createdBy, Instant now) {
        OperatorGroupJpaEntity e = new OperatorGroupJpaEntity();
        e.groupId = groupId;
        e.tenantId = tenantId;
        e.name = name;
        e.description = description;
        e.createdBy = createdBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /**
     * Rename / re-describe on a managed entity ({@code PATCH .../groups/{id}}). A {@code null}
     * argument leaves that field unchanged; {@code description} can only be changed, not
     * cleared, through this surface (matches the optional-both PATCH contract). Bumps
     * {@code updated_at}.
     */
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
