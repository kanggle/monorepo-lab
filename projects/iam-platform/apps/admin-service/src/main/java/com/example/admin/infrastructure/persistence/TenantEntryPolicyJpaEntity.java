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
 * <p>Row absent ⟺ policy off. S4 read it; S5 (the management API) writes it through
 * {@link #create} / {@link #apply} — turning the policy off keeps the row with {@code require_mfa = FALSE}.
 */
@Entity
@Table(name = "tenant_entry_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantEntryPolicyJpaEntity {

    /** A first write for {@code tenantId} (no row yet). */
    public static TenantEntryPolicyJpaEntity create(String tenantId, boolean requireMfa, Long updatedBy, Instant at) {
        TenantEntryPolicyJpaEntity e = new TenantEntryPolicyJpaEntity();
        e.tenantId = tenantId;
        e.requireMfa = requireMfa;
        e.updatedBy = updatedBy;
        e.updatedAt = at;
        return e;
    }

    /** A full-replace write over an existing row (the {@code @Version} guards concurrent writers). */
    public void apply(boolean newRequireMfa, Long newUpdatedBy, Instant at) {
        this.requireMfa = newRequireMfa;
        this.updatedBy = newUpdatedBy;
        this.updatedAt = at;
    }

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
