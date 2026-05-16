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

    /** Status transition (PATCH /operators/{id}/status). Bumps {@code updated_at}. */
    void changeStatus(long operatorInternalId, String newStatus, Instant at);

    /** Password change (PATCH /operators/me/password). Bumps {@code updated_at}. */
    void changePasswordHash(long operatorInternalId, String newPasswordHash, Instant at);

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
            Instant updatedAt
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
