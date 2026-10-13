package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
     * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — all provisioned operators
     * (non-null {@code oidc_subject}) for the email→account_id backfill. The
     * email-shape filter (contains {@code @}, not UUID-parseable) is applied in the
     * application layer, not here, so the email-shape rule is not duplicated into
     * persistence. {@code oidc_subject IS NULL} rows are excluded — they are
     * unprovisioned (nothing to backfill).
     */
    List<AdminOperatorJpaEntity> findByOidcSubjectIsNotNull();

    /**
     * TASK-BE-618 — any operator row (whatever its status) whose {@code oidc_subject} is this
     * account id (ADR-MONO-044 D5 self-onboarded operator).
     */
    boolean existsByOidcSubject(String oidcSubject);

    /**
     * TASK-BE-618 — any operator row (whatever its status) linked to this central identity
     * (ADR-MONO-034 U3 operator identity link).
     */
    boolean existsByIdentityId(String identityId);

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

    /**
     * TASK-MONO-784 S5 (AC-0 F15) — an operator row that is a MEMBER of {@code tenantId} (assignment row — same
     * membership predicate as {@link #findByTenantScope}) with this normalized {@code email}, any status, other
     * than {@code excludeId} (nullable = exclude nothing).
     */
    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
              FROM AdminOperatorJpaEntity o
             WHERE o.email = :email
               AND (:excludeId IS NULL OR o.id <> :excludeId)
               AND EXISTS (SELECT 1 FROM OperatorTenantAssignmentJpaEntity a
                           WHERE a.operatorId = o.id AND a.tenantId = :tenantId)
            """)
    boolean existsMemberWithEmail(@Param("tenantId") String tenantId,
                                  @Param("email") String email,
                                  @Param("excludeId") Long excludeId);

    /** Pagination for {@code GET /api/admin/operators} with optional status filter. */
    Page<AdminOperatorJpaEntity> findByStatus(String status, Pageable pageable);

    /**
     * TASK-MONO-175 / ADR-MONO-020 — tenant-scoped operator listing for
     * {@code GET /api/admin/operators?tenantId=X}. Returns operators that belong
     * to tenant {@code tenantId} = a MEMBERSHIP ({@code operator_tenant_assignment} row).
     * <b>TASK-MONO-784 S2 (ADR-MONO-082 b · R1 · D-2; admin-api.md § GET /api/admin/operators ①):</b>
     * the HOME tenant ({@code admin_operators.tenant_id}) is no longer an alternative — before 784 the
     * predicate was {@code home = X OR assignment = X} (BE-326). V0051 backfilled a home assignment for
     * every pre-784 operator, so existing lists are unchanged; an operator removed from its home company
     * leaves that company's list. The three sibling queries below share this one predicate.
     * {@code status} is an optional filter ({@code null} → no filter). Sort +
     * paging come from the {@link Pageable}. An explicit {@code countQuery} is
     * supplied so the EXISTS-subquery page count is derived correctly.
     */
    @Query(value = """
            SELECT o FROM AdminOperatorJpaEntity o
            WHERE (:status IS NULL OR o.status = :status)
              AND EXISTS (SELECT 1 FROM OperatorTenantAssignmentJpaEntity a
                          WHERE a.operatorId = o.id AND a.tenantId = :tenantId)
            """,
            countQuery = """
            SELECT COUNT(o) FROM AdminOperatorJpaEntity o
            WHERE (:status IS NULL OR o.status = :status)
              AND EXISTS (SELECT 1 FROM OperatorTenantAssignmentJpaEntity a
                          WHERE a.operatorId = o.id AND a.tenantId = :tenantId)
            """)
    Page<AdminOperatorJpaEntity> findByTenantScope(@Param("tenantId") String tenantId,
                                                   @Param("status") String status,
                                                   Pageable pageable);

    /**
     * TASK-MONO-771 S5 — the entry-policy pre-check roster: the {@code oidc_subject} (IAM account id, possibly
     * {@code null}) of every ACTIVE operator of {@code tenantId}, with the SAME membership predicate as
     * {@link #findByTenantScope} (assignment rows only since TASK-MONO-784 S2). One element per operator.
     */
    @Query("""
            SELECT o.oidcSubject FROM AdminOperatorJpaEntity o
            WHERE o.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM OperatorTenantAssignmentJpaEntity a
                          WHERE a.operatorId = o.id AND a.tenantId = :tenantId)
            """)
    List<String> findActiveOidcSubjectsInTenantScope(@Param("tenantId") String tenantId);

    /**
     * TASK-MONO-777 — {@code GET /api/admin/operators/lookup}: the ACTIVE, console-linked
     * ({@code oidc_subject} present) operators of tenant {@code tenantId} — same membership
     * predicate as {@link #findByTenantScope} (assignment rows only since TASK-MONO-784 S2) — whose normalized email
     * equals {@code email}. The {@code confined_tenant_id} filter (TASK-MONO-751) is applied
     * in the adapter so this query keeps the membership predicate byte-identical to the list.
     */
    @Query("""
            SELECT o FROM AdminOperatorJpaEntity o
            WHERE o.email = :email
              AND o.status = 'ACTIVE'
              AND o.oidcSubject IS NOT NULL
              AND EXISTS (SELECT 1 FROM OperatorTenantAssignmentJpaEntity a
                          WHERE a.operatorId = o.id AND a.tenantId = :tenantId)
            """)
    List<AdminOperatorJpaEntity> findLookupCandidatesInTenant(@Param("tenantId") String tenantId,
                                                              @Param("email") String email);

    /**
     * TASK-MONO-777 — the platform-scope ({@code '*'}) form of
     * {@link #findLookupCandidatesInTenant}: every tenant.
     */
    @Query("""
            SELECT o FROM AdminOperatorJpaEntity o
            WHERE o.email = :email
              AND o.status = 'ACTIVE'
              AND o.oidcSubject IS NOT NULL
            """)
    List<AdminOperatorJpaEntity> findLookupCandidatesAnyTenant(@Param("email") String email);
}
