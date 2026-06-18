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

    // TASK-BE-377 / ADR-MONO-035 O2 (step 4c): NULLABLE break-glass credential.
    // The operator's PRIMARY login is the unified IAM OIDC credential (exchanged into
    // an operator token via the ADR-014 token-exchange). The local password login is
    // RETAINED only as break-glass (emergency local login when the IdP/OIDC path is
    // unavailable). NULL = OIDC-only operator (no local password) — AdminLoginService
    // fail-closes such a row (INVALID_CREDENTIALS) so it must authenticate via OIDC.
    // Demoted, not removed (full removal is a deferred follow-up).
    @Column(name = "password_hash", length = 255)
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
    //
    // Flyway: V0029 (spec PR authored V0028, mechanically bumped to V0029 here
    // because db/migration-dev/V0028__seed_dev_operator_oidc_subject.sql was
    // already in the resolved location set; the column shape is byte-identical
    // to the spec).
    @Column(name = "finance_default_account_id", length = 36)
    private String financeDefaultAccountId;

    // TASK-BE-373 / ADR-MONO-034 U3 (step 3c): the central identities.identity_id
    // (account_db registry, step 3a) this operator is linked to. Value-convention
    // cross-DB reference (admin_db ≠ account_db → no FK possible; mirrors how
    // `oidc_subject` already references a consumer account_id). NULL = unlinked
    // (the default for every existing row; V0036 adds no backfill — the link is
    // opt-in/audited/reversible per U3). Set only via the link surface; cleared by
    // unlink. NOT a tenant-scope axis and NOT a role axis (identity ≠ authorization,
    // U5) — pure identity correlation.
    @Column(name = "identity_id", length = 36)
    private String identityId;

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
     * When a password is supplied, callers pass a pre-computed Argon2id hash; the
     * entity never sees the plaintext password. {@code passwordHash} may be
     * {@code null} (TASK-BE-377 / ADR-MONO-035 4c) — an OIDC-only operator with no
     * local break-glass password. {@code tenantId} is required (use
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

    /**
     * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — rewrite {@code oidc_subject}
     * (the email→account_id backfill). Invoked only by the internal maintenance
     * backfill endpoint after the new value (account_id) is resolved from
     * auth-service. Bumps {@code updated_at}; {@code @Version} drives the
     * optimistic-lock surface on a stale row.
     */
    public void setOidcSubject(String newOidcSubject, Instant at) {
        this.oidcSubject = newOidcSubject;
        this.updatedAt = at;
    }

    /** Password change invoked by {@code PATCH /operators/me/password}. */
    public void changePasswordHash(String newPasswordHash, Instant at) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = at;
    }

    /**
     * TASK-BE-306 — self-serve profile mutation invoked by
     * {@code PATCH /api/admin/operators/me/profile}. {@code newValue == null}
     * clears the column. Bumps {@code updated_at}; {@code @Version} drives
     * optimistic-lock surface on a stale row.
     */
    public void changeFinanceDefaultAccountId(String newValue, Instant at) {
        this.financeDefaultAccountId = newValue;
        this.updatedAt = at;
    }

    /**
     * TASK-BE-373 / ADR-MONO-034 U3 — link this operator to a central identity by
     * setting {@code identity_id}. Invoked by the opt-in audited link surface
     * ({@code PATCH /api/admin/operators/{operatorId}/identity:link}). Bumps
     * {@code updated_at}; {@code @Version} drives the optimistic-lock surface on a
     * stale row. The caller (use case) is responsible for the U3 authorization
     * checks (email-match necessary-not-sufficient, fail-closed identity resolve,
     * idempotency) — the entity only records the resolved link.
     */
    public void linkIdentity(String identityId, Instant at) {
        this.identityId = identityId;
        this.updatedAt = at;
    }

    /**
     * TASK-BE-373 / ADR-MONO-034 U3 — reverse the link by clearing
     * {@code identity_id} (the U6 "reversible until step 4" invariant). Invoked by
     * {@code PATCH /api/admin/operators/{operatorId}/identity:unlink}. Bumps
     * {@code updated_at}.
     */
    public void unlinkIdentity(Instant at) {
        this.identityId = null;
        this.updatedAt = at;
    }
}
