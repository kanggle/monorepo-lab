package com.example.admin.infrastructure.persistence.rbac;

import com.example.common.id.UuidV7;
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

@Entity
@Table(name = "admin_operators")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminOperatorJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // External operator identifier (UUID v7). This is the value carried in the
    // operator JWT `sub` claim; the internal BIGINT `id` is never exposed.
    @Column(name = "operator_id", length = 36, nullable = false, unique = true)
    private String operatorId;

    // TASK-BE-249: tenant_id for multi-tenant row-level isolation.
    // SUPER_ADMIN sentinel value is '*' (AdminOperator.PLATFORM_TENANT_ID).
    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    // Reserved for TASK-BE-029 (TOTP enrollment). NULL-only in this increment.
    @Column(name = "totp_enrolled_at")
    private Instant totpEnrolledAt;

    // TASK-BE-298 / ADR-MONO-014: GAP OIDC platform-console-web access token
    // `sub` (account_id UUID). The OIDC<->operator link key for
    // POST /api/admin/auth/token-exchange. NULL = console-token-exchange not
    // provisioned for this operator (fail-closed default). Platform-global
    // UNIQUE (uk_admin_operators_oidc_subject, V0027) — OIDC subject space is
    // tenant-independent. Link-only: tenant scope is `tenant_id`, never this.
    @Column(name = "oidc_subject", length = 255)
    private String oidcSubject;

    // TASK-BE-304: Operator's chosen default finance-platform account UUID,
    // emitted on console-registry-api § Per-operator profile attributes as the
    // finance product item's operatorContext.defaultAccountId. NULL = not
    // configured (Operator Overview finance card stays forbidden/MISSING_PREREQUISITE
    // per MVP option (b) per console-integration-contract.md § 2.4.9.1).
    // Set via a separate operator provisioning surface (out of scope here).
    // VARCHAR(36) opaque UUID; GAP does not verify against finance-platform —
    // stale ids surface as finance 404 ACCOUNT_NOT_FOUND. Classification:
    // internal (data-model.md § Data Classification Summary).
    @Column(name = "finance_default_account_id", length = 36)
    private String financeDefaultAccountId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // INT per data-model.md. Optimistic lock counter; monotonically increasing.
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    /**
     * Allocate a new external {@code operator_id} (UUID v7). Time-ordered per
     * RFC 9562 — see {@code specs/services/admin-service/rbac.md} D4 and task
     * TASK-BE-028c. Callers that persist a new operator row MUST obtain the
     * external id via this factory so that the 48-bit ms timestamp invariant
     * holds across provisioning paths.
     */
    public static String newOperatorId() {
        return UuidV7.randomString();
    }

    /**
     * Factory for {@code POST /api/admin/operators} (TASK-BE-083). Callers
     * must supply a pre-computed Argon2id hash; the entity never sees the
     * plaintext password.
     */
    /**
     * Factory for {@code POST /api/admin/operators} (TASK-BE-083).
     *
     * @deprecated Use {@link #create(String, String, String, String, String, String, Instant)}
     *             which requires {@code tenantId}. This overload defaults to {@code "fan-platform"}
     *             for backward-compat with legacy call sites that predate TASK-BE-249.
     */
    @Deprecated
    public static AdminOperatorJpaEntity create(String operatorId,
                                                String email,
                                                String passwordHash,
                                                String displayName,
                                                String status,
                                                Instant now) {
        return create(operatorId, email, passwordHash, displayName, status, "fan-platform", now);
    }

    /**
     * Factory for {@code POST /api/admin/operators} (TASK-BE-083 / TASK-BE-249).
     * Callers must supply a pre-computed Argon2id hash; the entity never sees the
     * plaintext password. {@code tenantId} is required (use
     * {@link com.example.admin.domain.rbac.AdminOperator#PLATFORM_TENANT_ID} for SUPER_ADMIN).
     */
    public static AdminOperatorJpaEntity create(String operatorId,
                                                String email,
                                                String passwordHash,
                                                String displayName,
                                                String status,
                                                String tenantId,
                                                Instant now) {
        AdminOperatorJpaEntity e = new AdminOperatorJpaEntity();
        e.operatorId = operatorId;
        e.tenantId = tenantId;
        e.email = email;
        e.passwordHash = passwordHash;
        e.displayName = displayName;
        e.status = status;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /** Status transition invoked by {@code PATCH /operators/{id}/status}. */
    public void changeStatus(String newStatus, Instant at) {
        this.status = newStatus;
        this.updatedAt = at;
    }

    /** Password change invoked by {@code PATCH /operators/me/password}. */
    public void changePasswordHash(String newPasswordHash, Instant at) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = at;
    }
}
