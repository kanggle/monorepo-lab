package com.example.admin.application.port;

import java.util.Optional;

/**
 * Resolves an operator's internal BIGINT PK from the external UUID
 * ({@code operator_id}). Keeps {@code admin_operators} JPA types out of the
 * application layer's import graph (architecture.md Allowed Dependencies).
 *
 * <p>Originally introduced by TASK-BE-030-fix for bulk-lock idempotency;
 * extended by TASK-BE-040-fix so refresh/logout services can translate between
 * the JWT {@code sub} UUID and the internal PK without reaching into
 * {@code infrastructure.persistence.rbac}.
 *
 * <p>TASK-BE-249: {@link OperatorLookupRef} extended with {@code tenantId} so the
 * application layer can perform tenant-scope checks without importing JPA entities.
 *
 * <p>TASK-BE-560: the projection was named {@code OperatorSummary} until it
 * collided — same simple name, unrelated shape — with
 * {@code OperatorQueryService.OperatorSummary}, the 9-field
 * {@code GET /api/admin/operators} listing DTO in the same application layer.
 * The listing DTO (API-facing, mirrored by {@code OperatorSummaryResponse})
 * keeps that name; this identity reference is {@code OperatorLookupRef}.
 */
public interface OperatorLookupPort {

    /**
     * @return internal BIGINT id of the operator, or empty when the UUID does
     *         not match any admin_operators row.
     */
    Optional<Long> findInternalId(String operatorId);

    /**
     * @return a {@link OperatorLookupRef} carrying both the internal PK, the
     *         external UUID, and the tenant scope when a matching row exists.
     */
    Optional<OperatorLookupRef> findByOperatorId(String operatorId);

    /**
     * Minimal projection exposed to the application layer. Never includes
     * credentials, status, or other persistence-only fields.
     *
     * <p>TASK-BE-249: {@code tenantId} added. Platform-scope operators
     * (SUPER_ADMIN) carry {@code "*"} as their tenantId.
     */
    record OperatorLookupRef(Long internalId, String operatorId, String tenantId) {
        /**
         * Legacy 2-arg constructor kept for call sites that predate TASK-BE-249.
         * Defaults {@code tenantId} to {@code "fan-platform"}.
         */
        public OperatorLookupRef(Long internalId, String operatorId) {
            this(internalId, operatorId, "fan-platform");
        }
    }
}
