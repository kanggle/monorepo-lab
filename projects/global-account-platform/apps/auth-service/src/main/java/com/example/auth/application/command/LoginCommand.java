package com.example.auth.application.command;

import com.example.auth.domain.session.SessionContext;

/**
 * Command for the login use case.
 *
 * <p>TASK-BE-229: {@code tenantId} is now an optional field. When provided, the login
 * is scoped to that specific tenant. When absent (null), the system checks for email
 * across all tenants; if multiple tenants have the same email the login returns
 * {@code LOGIN_TENANT_AMBIGUOUS} 400.
 */
public record LoginCommand(
        String email,
        String password,
        /** Optional tenant context. Null means "all tenants / auto-detect". */
        String tenantId,
        SessionContext sessionContext
) {
    /**
     * @deprecated Use {@link #LoginCommand(String, String, String, SessionContext)}.
     *             Retained for backwards compatibility; defaults tenantId to null (auto-detect).
     */
    @Deprecated
    public LoginCommand(String email, String password, SessionContext sessionContext) {
        this(email, password, null, sessionContext);
    }
}
