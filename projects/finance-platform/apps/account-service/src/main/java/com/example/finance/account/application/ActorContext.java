package com.example.finance.account.application;

import com.example.finance.account.domain.account.ActorType;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Value object —
 * keeps Spring Security types out of the application layer.
 */
public record ActorContext(String accountId, String tenantId, Set<String> roles) {

    /** TASK-MONO-794 — the account write role; KYC upgrade and top-up require it (owner decision Q3). */
    public static final String ACCOUNT_WRITE_ROLE = "FINANCE_ACCOUNT_WRITE";

    /**
     * TASK-MONO-794 (ADR-MONO-083 P7) — the Finance entry role (derived by auth-service when ≥1 business role rides the
     * token) plus the five business roles (finance {@code iam-integration.md} § 업무 역할).
     */
    private static final Set<String> FINANCE_OPERATOR_ROLES = Set.of(
            "FINANCE_OPERATOR",
            "FINANCE_ACCOUNT_READ", ACCOUNT_WRITE_ROLE,
            "FINANCE_LEDGER_READ", "FINANCE_LEDGER_WRITE", "FINANCE_LEDGER_CONTROL");

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Whether the caller acts as an operator (audit {@link ActorType#OPERATOR}) rather than an account holder.
     *
     * <p>TASK-MONO-417 added the assume-tenant {@code FINANCE_OPERATOR}. TASK-MONO-794 (ADR-MONO-083 P7): that role is
     * now the auth-derived ENTRY role and any Finance business role marks an operator; the generic
     * {@code OPERATOR/ADMIN/SUPER_ADMIN} strings are issued by nobody and no longer count (Failure 1). This method
     * decides the audit actor type only — it does NOT authorise anything ({@link #canOperateAccounts()} does).
     */
    public boolean isOperator() {
        return roles != null && roles.stream().anyMatch(FINANCE_OPERATOR_ROLES::contains);
    }

    /**
     * TASK-MONO-794 (owner decision Q3) — the operator-only account operations (KYC upgrade, top-up) require the
     * account write ROLE. A scope-only workload token (no roles) and an operator holding only other Finance roles are
     * refused.
     */
    public boolean canOperateAccounts() {
        return hasRole(ACCOUNT_WRITE_ROLE);
    }

    /** Map to a domain {@link ActorType} for audit/history rows (F6). */
    public ActorType actorType() {
        return isOperator() ? ActorType.OPERATOR : ActorType.HOLDER;
    }
}
