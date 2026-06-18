package com.example.admin.application.port;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.RoleNotFoundException;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TASK-BE-288 — port over the {@code admin_operators} + {@code admin_roles} +
 * {@code admin_operator_roles} tables. Keeps {@code AdminOperator*JpaEntity} /
 * {@code AdminRole*JpaEntity} / {@code AdminOperator*JpaRepository} out of the
 * application layer so use cases depend only on this port and its inner
 * projections.
 *
 * <p>Mutating methods are exposed as id-addressed operations
 * ({@code changeStatus} / {@code changePasswordHash} / {@code deleteOperatorRoles}
 * / {@code saveOperatorRoles}) so the application layer never holds a JPA
 * entity reference.
 *
 * <p>Read methods return immutable {@link OperatorView} / {@link RoleView}
 * projections; bulk loaders return pre-flattened {@code Map}s that match the
 * existing query shapes in {@code OperatorQueryService}.
 */
public interface AdminOperatorPort {

    // ---------- Operator ----------

    /** Look up an operator by external UUID (JWT {@code sub} claim). */
    Optional<OperatorView> findByOperatorId(String operatorUuid);

    /**
     * TASK-BE-298 / ADR-MONO-014 — look up an operator by the GAP OIDC
     * {@code platform-console-web} subject ({@code sub} = account_id UUID).
     * The deterministic OIDC&lt;-&gt;operator link for
     * {@code POST /api/admin/auth/token-exchange}. {@code oidc_subject} is a
     * platform-global UNIQUE column (V0027) so this resolves to at most one
     * row; an empty result is the fail-closed branch (caller mints no token).
     * The caller is responsible for the {@code status == ACTIVE} fail-closed
     * check ({@link OperatorView#status()}).
     */
    Optional<OperatorView> findByOidcSubject(String oidcSubject);

    /**
     * Per-tenant email uniqueness pre-check matching the
     * {@code (tenant_id, email)} composite unique index introduced by V0025.
     */
    boolean existsByTenantIdAndEmail(String tenantId, String email);

    /**
     * Paginated listing with optional status filter — sorted by
     * {@code createdAt DESC} (admin-api pagination contract).
     *
     * @param statusFilter null/blank → no filter
     */
    OperatorPage findOperatorsPage(String statusFilter, int page, int size);

    /**
     * TASK-MONO-175 / ADR-MONO-020 — tenant-scoped paginated listing: operators
     * whose HOME tenant ({@code admin_operators.tenant_id}) equals {@code tenantId}
     * OR who have an ASSIGNED tenant ({@code operator_tenant_assignment}) equal to
     * {@code tenantId}. Sorted by {@code createdAt DESC}. Used by
     * {@code GET /api/admin/operators} when scoping to a specific (non-platform)
     * tenant; the unscoped {@link #findOperatorsPage} is used for the platform
     * ({@code '*'}) cross-tenant view.
     *
     * @param statusFilter null/blank → no filter
     */
    OperatorPage findOperatorsPageByTenant(String tenantId, String statusFilter, int page, int size);

    /**
     * Persist a new operator row + flush. On {@code (tenant_id, email)} unique
     * constraint collision, throws {@link OperatorEmailConflictException}
     * directly (matches the legacy
     * {@code DataIntegrityViolationException → OperatorEmailConflictException}
     * translation in {@code CreateOperatorUseCase}).
     *
     * @return the persisted projection (populated {@code internalId},
     *         {@code createdAt}, {@code updatedAt}).
     */
    OperatorView createOperator(NewOperator row);

    /**
     * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — every operator whose
     * {@code oidc_subject} is non-null (provisioned), as a lightweight
     * {@link OperatorOidcSubjectView} projection (the only fields the backfill
     * needs). The backfill use case filters the email-shaped subset in the
     * application layer (the email-shape rule — contains {@code @}, not
     * UUID-parseable — is a domain decision kept out of the persistence layer).
     * Read-only; ordering is implementation-defined. {@code oidc_subject IS NULL}
     * rows are excluded (unprovisioned — nothing to backfill).
     */
    List<OperatorOidcSubjectView> findOperatorsWithOidcSubject();

    /**
     * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — set
     * {@code admin_operators.oidc_subject = newOidcSubject} on the row identified by
     * {@code operatorInternalId} (the email→account_id backfill write). Bumps
     * {@code updated_at}. Same load-modify-{@code saveAndFlush} pattern as
     * {@link #changeStatus} so the UPDATE flushes WITHIN the request transaction.
     */
    void updateOidcSubject(long operatorInternalId, String newOidcSubject, Instant at);

    /** Status transition (PATCH /operators/{id}/status). Bumps {@code updated_at}. */
    void changeStatus(long operatorInternalId, String newStatus, Instant at);

    /** Password change (PATCH /operators/me/password). Bumps {@code updated_at}. */
    void changePasswordHash(long operatorInternalId, String newPasswordHash, Instant at);

    /**
     * TASK-BE-306 — self-serve profile mutation
     * (PATCH /api/admin/operators/me/profile).
     *
     * <p>Sets {@code admin_operators.finance_default_account_id} on the row
     * identified by {@code operatorInternalId}. {@code newValue == null}
     * clears the column; a non-null value must already have been validated
     * upstream (length, control chars, whitespace).
     *
     * <p>Bumps {@code updated_at}. On {@code admin_operators.version} race
     * the underlying JPA save surfaces
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * which the exception handler maps to {@code 409 OPTIMISTIC_LOCK_CONFLICT}.
     */
    void changeFinanceDefaultAccountId(long operatorInternalId, String newValue, Instant at);

    /**
     * TASK-BE-373 / ADR-MONO-034 U3 (step 3c) — set {@code admin_operators.identity_id}
     * on the row identified by {@code operatorInternalId} (link the operator to a
     * central identity). Bumps {@code updated_at}.
     *
     * <p>This is the persistence side of the opt-in audited link operation; the
     * authorization decision (email-match necessary-not-sufficient, fail-closed
     * identity resolve, idempotency) is made by the use case BEFORE this call. The
     * load-modify-{@code saveAndFlush} pattern mirrors {@link #changeStatus} so the
     * managed-entity UPDATE flushes WITHIN the request transaction (see the adapter
     * for the TASK-BE-335 explicit-flush rationale).
     */
    void linkIdentity(long operatorInternalId, String identityId, Instant at);

    /**
     * TASK-BE-373 / ADR-MONO-034 U3 — clear {@code admin_operators.identity_id}
     * (reverse the link, U6 reversibility). Bumps {@code updated_at}. Same
     * load-modify-{@code saveAndFlush} pattern as {@link #changeStatus}.
     */
    void unlinkIdentity(long operatorInternalId, Instant at);

    // ---------- Roles ----------

    /**
     * Resolve role names → views preserving the requested ordering. Blank names
     * are skipped; duplicates collapse to first occurrence.
     *
     * @throws RoleNotFoundException when any non-blank name is unknown.
     */
    Map<String, RoleView> resolveRolesByName(List<String> roleNames);

    /** All roles bound to {@code operatorInternalId}. Order is implementation-defined. */
    List<RoleView> findRolesForOperator(long operatorInternalId);

    /**
     * Returns {@code true} if any role currently bound to {@code operatorInternalId}
     * has {@code require_2fa = true}. Equivalent to the legacy
     * {@code AdminLoginService.roleSetRequires2fa} helper.
     */
    boolean anyRoleRequires2fa(long operatorInternalId);

    /**
     * Bulk-load role names per operator. Used by {@code GET /api/admin/operators}
     * to avoid N+1 — returns a map keyed by the internal BIGINT operator id.
     * Names are sorted alphabetically within each operator's list (matches the
     * legacy {@code OperatorQueryService.bulkLoadRoles} sort).
     */
    Map<Long, List<String>> bulkLoadRoleNamesByOperator(Collection<Long> operatorInternalIds);

    // ---------- Operator-Role bindings ----------

    /** Delete every role binding for one operator (full-replacement pattern). */
    void deleteOperatorRoles(long operatorInternalId);

    /** Persist the given bindings in one batch. */
    void saveOperatorRoles(List<NewRoleBinding> bindings);

    // ---------- Helpers ----------

    /**
     * Resolve the internal BIGINT id of an actor performing an action — used as
     * {@code granted_by} for new operator-role bindings. Equivalent to the
     * legacy TASK-BE-121 {@code resolveActorInternalId} helper.
     *
     * @return internal id, or {@code null} when the operator UUID is null/blank
     *         or no admin_operators row matches.
     */
    Long resolveActorInternalId(String operatorUuid);

    // ---------- Projections ----------

    /**
     * Immutable projection of an {@code admin_operators} row. Carries every
     * field the application layer needs across login / patch / query paths.
     * Never exposes JPA-only fields.
     *
     * <p>{@code financeDefaultAccountId} (TASK-BE-308) carries the
     * {@code finance_default_account_id} column value so the
     * {@code GET /api/admin/operators} list response can expose each operator's
     * current {@code operatorContext.defaultAccountId} without an additional
     * round-trip. {@code null} when the column is NULL; the SELECT already
     * targets the full entity row so no N+1 risk is introduced.
     */
    record OperatorView(
            long internalId,
            String operatorId,
            String tenantId,
            String email,
            String passwordHash,
            String displayName,
            String status,
            Instant totpEnrolledAt,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            String financeDefaultAccountId,
            /**
             * TASK-BE-373 / ADR-MONO-034 U3 — the central identities.identity_id this
             * operator is linked to (value-convention cross-DB ref), or {@code null}
             * when unlinked. Read by the link/unlink use cases for the idempotency /
             * already-linked-to-different checks.
             */
            String identityId
    ) {}

    /**
     * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — lightweight projection for the
     * {@code oidc_subject} email→account_id backfill: only the row's internal id
     * (for the targeted UPDATE), external operator id + tenant (for the PII-safe
     * audit log + tenant-scoped account_id resolution), and the current
     * {@code oidc_subject} (the email-shape filter input).
     */
    record OperatorOidcSubjectView(
            long internalId,
            String operatorId,
            String tenantId,
            String oidcSubject
    ) {}

    /** Value used to INSERT a new operator row. */
    record NewOperator(
            String operatorId,
            String tenantId,
            String email,
            String passwordHash,
            String displayName,
            String status,
            Instant createdAt
    ) {}

    /** Immutable projection of an {@code admin_roles} row. */
    record RoleView(
            long id,
            String name,
            String description,
            boolean require2fa
    ) {}

    /** Value used to INSERT an {@code admin_operator_roles} binding. */
    record NewRoleBinding(
            long operatorInternalId,
            long roleId,
            Instant grantedAt,
            Long grantedBy,
            String tenantId
    ) {}

    /**
     * Page projection matching the legacy
     * {@code OperatorQueryService.OperatorPage} shape.
     */
    record OperatorPage(
            List<OperatorView> content,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {}
}
