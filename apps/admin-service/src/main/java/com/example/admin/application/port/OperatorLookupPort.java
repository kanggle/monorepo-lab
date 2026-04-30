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
 */
public interface OperatorLookupPort {

    /**
     * @return internal BIGINT id of the operator, or empty when the UUID does
     *         not match any admin_operators row.
     */
    Optional<Long> findInternalId(String operatorId);

    /**
     * @return a {@link OperatorSummary} carrying both the internal PK and the
     *         external UUID when a matching row exists.
     */
    Optional<OperatorSummary> findByOperatorId(String operatorId);

    /**
     * Minimal projection exposed to the application layer. Never includes
     * credentials, status, or other persistence-only fields.
     */
    record OperatorSummary(Long internalId, String operatorId) {}
}
