package com.example.auth.infrastructure.oauth2.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity backing the {@code oauth_scopes} table.
 *
 * <p>System scopes (openid, profile, email, offline_access) have
 * {@code tenantId = null} and are shared across all tenants.
 * Tenant-specific scopes carry the owning {@code tenantId}.
 *
 * <p>The unique constraint is enforced at the DB level via the
 * {@code tenant_scope_key} generated column in the migration.
 *
 * <p>TASK-BE-252.
 */
@Entity
@Table(name = "oauth_scopes")
@Getter
@Setter
@NoArgsConstructor
public class OAuthScopeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "scope_name", length = 100, nullable = false)
    private String scopeName;

    /** NULL = system scope shared across tenants. */
    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
