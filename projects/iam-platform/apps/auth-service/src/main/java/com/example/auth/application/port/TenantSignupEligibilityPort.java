package com.example.auth.application.port;

/**
 * TASK-BE-581 — answers "may the browser signup surface be OFFERED for this tenant?".
 *
 * <p><b>Why this exists.</b> {@code login.html} offered self-service signup unconditionally
 * (TASK-BE-470). For a browser flow started by {@code platform-console-web} that offer can
 * never be honoured: the console's {@code oauth_clients.tenant_id} is {@code iam}, the
 * platform's own <i>reserved</i> operational slug, and no {@code tenants} row for it exists
 * or may ever be created ({@code V0024}, {@code multi-tenancy.md} reserved-word set,
 * {@code CreateTenantUseCase.RESERVED}). Signup therefore returned
 * {@code 404 TENANT_NOT_FOUND} 100% of the time.
 *
 * <p><b>The predicate is deliberately the backend's own.</b> account-service decides whether
 * an account may be born in a tenant with {@code ActiveTenantGuard}: the row must
 * <b>exist</b> AND be <b>ACTIVE</b>. This port asks the same two questions of the same
 * record, rather than testing membership in a hardcoded reserved-slug list. A list would
 * answer "is this one of the values we thought about", which is a different question that
 * goes stale, and would miss the suspended-tenant case entirely.
 * TASK-BE-580 measured what that case actually is: account-service returns
 * {@code 409 TENANT_SUSPENDED}, not the {@code 403} assumed here originally — and 409 was
 * already being read as "email already registered", so it was the worse of the two defects.
 *
 * <p><b>This is a UX gate, not an authorization boundary.</b> {@code ActiveTenantGuard}
 * remains the authority; nothing here can admit an account the backend would reject. That
 * is why the implementation fails <i>open</i> on an account-service outage — see
 * {@code TenantSignupEligibilityResolver}.
 */
public interface TenantSignupEligibilityPort {

    /**
     * @param tenantId the tenant the browser flow would create the account in — as resolved
     *                 from the initiating OIDC client by {@code SavedRequestTenantResolver}
     * @return {@code true} when signup may be offered for {@code tenantId}
     */
    boolean isSignupOffered(String tenantId);
}
