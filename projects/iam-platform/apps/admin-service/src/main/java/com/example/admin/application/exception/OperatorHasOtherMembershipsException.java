package com.example.admin.application.exception;

/**
 * TASK-MONO-784 S4 (rider R3 · decision D-8) — 403 {@code OPERATOR_HAS_OTHER_MEMBERSHIPS}: a change to a
 * row-wide attribute of an operator (status · {@code finance_default_account_id} · identity link) whose target
 * belongs to two or more companies, attempted by an actor whose grant scope is not {@code '*'} but covers at
 * least one of those companies. Such an actor removes the person from its own company instead
 * ({@code DELETE …/assignments/{tenantId}} — «이 회사에서 내보내기»).
 *
 * <p>An actor that administers none of the target's companies gets {@code TENANT_SCOPE_DENIED} instead, so the
 * membership count is never disclosed outside the companies involved (admin-api.md § 행동 테넌트 «행 전체 규칙»).
 */
public class OperatorHasOtherMembershipsException extends RuntimeException {
    public OperatorHasOtherMembershipsException(String message) {
        super(message);
    }
}
