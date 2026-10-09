package com.example.admin.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TASK-MONO-771 (ADR-MONO-080 D4 · R2) — JPA entity for {@code tenant_entry_policy} (Flyway V0047,
 * data-model.md § {@code tenant_entry_policy}). Lives under the {@code JpaConfig} scan base so it is
 * validated by {@code ddl-auto=validate}.
 *
 * <p>Read-only in S4 (no write path — the management API is S5). Row absent ⟺ policy off.
 */
@Entity
@Table(name = "tenant_entry_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantEntryPolicyJpaEntity {

    @Id
    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "require_mfa", nullable = false)
    private boolean requireMfa;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private int version;
}
