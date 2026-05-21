package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;

import java.time.Instant;

/**
 * Shared test helpers for the operator administration use case unit tests.
 *
 * <p>Post TASK-BE-288 — {@code OperatorRoleResolver} was inlined into
 * {@link AdminOperatorPort}, so this support class now only carries the actor
 * fixture and the port-projection factories ({@code OperatorView},
 * {@code RoleView}). Tests mock {@link AdminOperatorPort} directly.
 *
 * <p>Package-private by design — only collaborators inside
 * {@code com.example.admin.application} test package may use it.
 */
final class OperatorUseCaseTestSupport {

    private OperatorUseCaseTestSupport() {
        // utility — no instances
    }

    /** Default actor used by the operator-admin use case tests. */
    static OperatorContext actor() {
        return new OperatorContext("actor-uuid", "jti-1");
    }

    /** Build a {@link AdminOperatorPort.RoleView} fixture. */
    static AdminOperatorPort.RoleView role(long id, String name) {
        return new AdminOperatorPort.RoleView(id, name, name, false);
    }

    /** Build a {@link AdminOperatorPort.RoleView} fixture with explicit require2fa flag. */
    static AdminOperatorPort.RoleView role(long id, String name, boolean require2fa) {
        return new AdminOperatorPort.RoleView(id, name, name, require2fa);
    }

    /**
     * Build an {@link AdminOperatorPort.OperatorView} fixture mirroring the
     * defaults that the legacy {@code AdminOperatorJpaEntity.create} factory
     * stamped: {@code passwordHash="hash"}, {@code displayName="Display"},
     * {@code tenantId="fan-platform"}, {@code createdAt=2026-01-01T00:00Z}.
     */
    static AdminOperatorPort.OperatorView operator(long id, String uuid, String email, String status) {
        return operator(id, uuid, email, status, "fan-platform");
    }

    /** Same as {@link #operator(long, String, String, String)} with explicit tenantId. */
    static AdminOperatorPort.OperatorView operator(long id, String uuid, String email,
                                                   String status, String tenantId) {
        return operator(id, uuid, email, status, tenantId, null);
    }

    /**
     * TASK-BE-308 — overload accepting an explicit
     * {@code financeDefaultAccountId} so list-projection tests can construct
     * fixtures with the column populated. Default overloads pass {@code null}.
     */
    static AdminOperatorPort.OperatorView operator(long id, String uuid, String email,
                                                   String status, String tenantId,
                                                   String financeDefaultAccountId) {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        return new AdminOperatorPort.OperatorView(
                id, uuid, tenantId, email, "hash", "Display", status,
                null, null, created, created, financeDefaultAccountId);
    }
}
