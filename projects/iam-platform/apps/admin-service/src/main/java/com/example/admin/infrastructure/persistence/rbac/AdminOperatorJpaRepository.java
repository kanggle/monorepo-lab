package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminOperatorJpaRepository extends JpaRepository<AdminOperatorJpaEntity, Long> {
    Optional<AdminOperatorJpaEntity> findByEmail(String email);

    /**
     * TASK-BE-353 (ADR-MONO-029) — the raw {@code tags} column for the target
     * operator (RESOURCE_TAG access condition). A <b>native projection</b> so no
     * entity field is added: the aspect's {@code ResourceTagResolver} splits the
     * comma-separated string into a tag set. Returns the (possibly {@code null} /
     * empty) tags string when the operator exists; {@code Optional.empty()} when it
     * does not. {@code tags} is itself nullable, so the projected value may be
     * {@code null} even for a present row (an untagged operator).
     */
    @Query(value = "SELECT tags FROM admin_operators WHERE operator_id = :operatorId", nativeQuery = true)
    Optional<String> findTagsByOperatorId(@Param("operatorId") String operatorId);

    /**
     * Look up an operator by the external UUID (JWT {@code sub} claim).
     * The internal BIGINT {@code id} is never exposed to callers.
     */
    Optional<AdminOperatorJpaEntity> findByOperatorId(String operatorId);

    /**
     * TASK-BE-298 / ADR-MONO-014 — look up an operator by the GAP OIDC
     * {@code platform-console-web} subject ({@code sub} = account_id UUID).
     * The deterministic OIDC&lt;-&gt;operator link for
     * {@code POST /api/admin/auth/token-exchange}. {@code oidc_subject} is a
     * platform-global UNIQUE column (V0027), so this resolves to at most one
     * row; a missing row is the fail-closed branch (no token minted).
     */
    Optional<AdminOperatorJpaEntity> findByOidcSubject(String oidcSubject);

    /**
     * Per-tenant email uniqueness check. Replaces the legacy single-column
     * {@link #existsByEmail(String)} after V0025 changed the unique index to
     * {@code (tenant_id, email)}.
     *
     * @param tenantId the tenant scope to check within
     * @param email    normalized (trimmed, lower-cased) email address
     */
    boolean existsByTenantIdAndEmail(String tenantId, String email);

    /**
     * @deprecated Use {@link #existsByTenantIdAndEmail(String, String)} instead.
     *             This method performs a global email check across all tenants,
     *             which contradicts the per-tenant unique constraint introduced
     *             by V0025. Kept only until all callers are migrated.
     */
    @Deprecated
    boolean existsByEmail(String email);

    /** Pagination for {@code GET /api/admin/operators} with optional status filter. */
    Page<AdminOperatorJpaEntity> findByStatus(String status, Pageable pageable);

    /**
     * TASK-MONO-175 / ADR-MONO-020 — tenant-scoped operator listing for
     * {@code GET /api/admin/operators?tenantId=X}. Returns operators that belong
     * to tenant {@code tenantId} = their HOME tenant ({@code admin_operators.tenant_id})
     * OR an ASSIGNED tenant ({@code operator_tenant_assignment} — D1 N:M, BE-326).
     * {@code status} is an optional filter ({@code null} → no filter). Sort +
     * paging come from the {@link Pageable}. An explicit {@code countQuery} is
     * supplied so the EXISTS-subquery page count is derived correctly.
     */
    @Query(value = """
            SELECT o FROM AdminOperatorJpaEntity o
            WHERE (:status IS NULL OR o.status = :status)
              AND ( o.tenantId = :tenantId
                 OR EXISTS (SELECT 1 FROM OperatorTenantAssignmentJpaEntity a
                            WHERE a.operatorId = o.id AND a.tenantId = :tenantId) )
            """,
            countQuery = """
            SELECT COUNT(o) FROM AdminOperatorJpaEntity o
            WHERE (:status IS NULL OR o.status = :status)
              AND ( o.tenantId = :tenantId
                 OR EXISTS (SELECT 1 FROM OperatorTenantAssignmentJpaEntity a
                            WHERE a.operatorId = o.id AND a.tenantId = :tenantId) )
            """)
    Page<AdminOperatorJpaEntity> findByTenantScope(@Param("tenantId") String tenantId,
                                                   @Param("status") String status,
                                                   Pageable pageable);
}
