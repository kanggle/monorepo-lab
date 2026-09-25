package com.example.auth.application.port;

import com.example.auth.application.result.AccountProfileResult;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.AccountStatusWithTenantLookupResult;
import com.example.auth.application.result.SocialSignupResult;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for communicating with account-service.
 * Implementation lives in infrastructure/client/.
 */
public interface AccountServicePort {

    /**
     * TASK-BE-470-fix-001: creates a new account via the public
     * {@code POST /api/accounts/signup} endpoint on behalf of the browser signup
     * page (server-side proxy). Unlike the SAS browser pages, {@code /api/accounts}
     * is not served on the auth-service origin, so a client-side {@code fetch} from
     * {@code /signup} cannot reach it; auth-service proxies the call here so the whole
     * flow stays same-origin (like the {@code /login} form).
     *
     * <p>This targets the <b>public</b> signup endpoint (no bearer token), distinct
     * from the {@code /internal/**} calls the other port methods make.
     *
     * <p>TASK-BE-507: {@code tenantId} is the tenant of the OIDC client the user is
     * registering through — resolved from the saved {@code /oauth2/authorize} request by
     * {@code SavedRequestTenantResolver} and sent as {@code X-Tenant-Id}. Before BE-507 no
     * tenant crossed this hop, so every consumer (including every ecommerce shopper) was born
     * {@code fan-platform}. A {@code null} keeps that old default.
     *
     * @param email       the new account email
     * @param password    the raw password (account-service + auth-service PasswordPolicy validate)
     * @param displayName the optional display name (nullable/blank → omitted)
     * @param tenantId    the tenant to create the account in (nullable → account-service pins fan-platform)
     * @throws com.example.auth.application.exception.SignupEmailConflictException on 409 (email taken)
     * @throws com.example.auth.application.exception.SignupInvalidException on 400/422 (validation)
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException on 5xx / timeout / IO
     */
    void signup(String email, String password, String displayName, String tenantId);

    /**
     * Looks up an account's current status by id, without a tenant — account-service then
     * pins the lookup to {@code fan-platform} (the pre-BE-507 behaviour). Equivalent to
     * {@code getAccountStatus(accountId, null)}.
     *
     * <p>TASK-BE-063: replaces the previous email-based credential lookup. The
     * login path now resolves email → credential locally, then calls this to
     * verify the account is still ACTIVE.</p>
     *
     * @see #getAccountStatus(String, String)
     */
    default Optional<AccountStatusLookupResult> getAccountStatus(String accountId) {
        return getAccountStatus(accountId, null);
    }

    /**
     * Looks up an account's current status by id within {@code tenantId}
     * ({@code GET /internal/accounts/{id}/status} with {@code X-Tenant-Id}).
     *
     * <p><b>TASK-BE-600 — "empty" means exactly one thing: 404.</b> Before BE-600 an empty
     * result also stood for a non-404 4xx and for a 200 with no usable {@code status}, and
     * the social path read every empty as "status unavailable, skip the guard" (BE-063). A
     * rejected or unreadable lookup is a FAILED lookup, not an answer, so it now throws
     * {@link com.example.auth.application.exception.AccountServiceUnavailableException} like a
     * 5xx / timeout / open circuit does — and both login paths fail closed on it (owner
     * decision, TASK-BE-600 AC-2).
     *
     * <p>404 stays empty because it is a legitimate answer on the login path: console
     * operator credentials (tenant {@code iam}) have no {@code accounts} row by design, and a
     * caller that sends no tenant gets a {@code fan-platform}-pinned lookup that cannot see an
     * account born in another tenant.
     *
     * @param accountId the account to check
     * @param tenantId  the tenant the account lives in; {@code null}/blank → no header →
     *                  account-service pins {@code fan-platform}
     * @return the account's status, or empty if account-service answered 404
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if the
     *         lookup failed (5xx / timeout / circuit-open / IO / non-404 4xx / unusable body)
     */
    Optional<AccountStatusLookupResult> getAccountStatus(String accountId, String tenantId);

    /**
     * TASK-BE-602 — looks up an account's status AND the tenant it actually lives in, by id alone
     * ({@code GET /internal/accounts/{id}/status-with-tenant}). The tenant is an output here, never
     * an input.
     *
     * <p>For the social-login callback, which knows the account id but has no trustworthy source
     * for the account's tenant: the initiating client's tenant and the social-identity row's tenant
     * both disagree with the {@code accounts} row for accounts born before TASK-BE-507 (owner
     * decision, TASK-BE-602 AC-0). It replaces that path's {@link #getAccountStatus(String)} call —
     * one account-service round trip per social login, as before.
     *
     * <p>Failure mapping is the TASK-BE-600 one of {@link #getAccountStatus(String, String)}:
     * <b>404 → empty</b> (an answer: no account row in any tenant — the login applies no status rule);
     * everything else that is not a usable 200 — non-404 4xx, a 200 without {@code status} or
     * {@code tenantId}, 5xx, timeout, open circuit, IO — throws
     * {@link com.example.auth.application.exception.AccountServiceUnavailableException}, and the
     * login fails closed.
     *
     * @param accountId the account to check
     * @return the account's status and own tenant, or empty if account-service answered 404
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if the
     *         lookup failed
     */
    Optional<AccountStatusWithTenantLookupResult> getAccountStatusAndTenant(String accountId);

    /**
     * Creates or retrieves an account for social login via internal HTTP to account-service.
     * If an account with the given email already exists, returns the existing accountId.
     * If not, creates a new account and returns the new accountId.
     *
     * <p>TASK-BE-507: {@code tenantId} is the tenant already resolved from the initiating OIDC
     * client for the social-identity row and the token ({@code SavedRequestTenantResolver}).
     * It was previously dropped on this hop, so the account row said {@code fan-platform}
     * while the token said {@code ecommerce} — the two now agree.
     *
     * @param email          the user's email from the OAuth provider
     * @param provider       the OAuth provider name (e.g., "GOOGLE", "KAKAO")
     * @param providerUserId the user's unique ID from the OAuth provider
     * @param displayName    the user's display name from the OAuth provider (nullable)
     * @param tenantId       the tenant to create the account in (nullable → account-service pins fan-platform)
     * @return social signup result with accountId, status, and whether it's a new account
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    SocialSignupResult socialSignup(String email, String provider, String providerUserId,
                                    String displayName, String tenantId);

    /**
     * Retrieves the full profile of an account for OIDC userinfo response construction.
     *
     * <p>Called by {@link com.example.auth.infrastructure.oauth2.OidcUserInfoMapper} when a
     * request hits {@code GET /oauth2/userinfo} with a valid bearer token that contains
     * {@code scope=openid}. The returned profile is mapped to standard OIDC claims
     * (sub, email, name, preferred_username, locale).</p>
     *
     * <p>TASK-BE-251 Phase 2a — authorization_code flow + /oauth2/userinfo endpoint.</p>
     *
     * @param accountId the account identifier (JWT {@code sub} claim)
     * @return the account profile, or empty if the account does not exist
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    Optional<AccountProfileResult> getAccountProfile(String accountId);

    /**
     * Returns the <b>effective</b> entitled product/domain keys for a tenant —
     * {@code ACTIVE subscriptions ∩ effectiveCeiling(tenant)}.
     *
     * <p>TASK-BE-324 (ADR-MONO-019 § 3.3 keystone): called by
     * {@link com.example.auth.infrastructure.oauth2.TenantClaimTokenCustomizer} at
     * {@code authorization_code}/{@code refresh_token} issuance time to populate the
     * signed {@code entitled_domains} claim.</p>
     *
     * <p>TASK-BE-491 (ADR-MONO-047 § D6): now calls
     * {@code GET /internal/tenants/<tid>/entitled-domains} and extracts {@code domainKeys[]}.
     * account-service applies the org-node entitlement ceiling there — the single point of
     * enforcement — so this port's <b>signature and every consumer are byte-unchanged</b>, and
     * {@code derive(E ∩ C) = derive(E) ∩ derive(C)} because ADR-035 derivation is per-domain.
     * The older {@code GET /internal/tenant-domain-subscriptions} still returns the RAW ACTIVE
     * rows for the console catalog and subscription management; only this token-issuance leg
     * moved.</p>
     *
     * <p>Throws {@link com.example.auth.application.exception.AccountServiceUnavailableException}
     * on account-service failure (5xx / circuit-open / timeout / IO) — consistent with the
     * sibling methods. The <b>caller decides fail-soft</b>: the token customizer catches this
     * and omits the claim so token issuance never depends on account-service availability. A
     * failure can therefore only narrow reach (the gateway 403s); it can never widen it.</p>
     *
     * @param tenantId the tenant whose effective entitled domains to resolve
     * @return the effective entitled domainKeys (possibly empty)
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    List<String> listEntitledDomains(String tenantId);

    /**
     * ADR-MONO-033 S2: roles source for JWT issuance, called by the (future)
     * TenantClaimTokenCustomizer roles leg.
     * GET /internal/tenants/{tid}/accounts/{aid}/roles, extracts roles[].
     * Throws AccountServiceUnavailableException on failure — the caller fail-softs (ADR-033 S5).
     *
     * @param tenantId  the tenant scope
     * @param accountId the account whose roles to resolve
     * @return the role names assigned to the account (possibly empty)
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException if account-service is down
     */
    List<String> listAccountRoles(String tenantId, String accountId);

    /**
     * Resolves the authoritative {@code tenant_type} for a tenant from
     * account-service (TASK-BE-407). Calls {@code GET /internal/tenants/{tenantId}}
     * and extracts the {@code tenantType} field ("B2C_CONSUMER" | "B2B_ENTERPRISE").
     *
     * <p>Replaces the previous hardcoded 2-value fallback in
     * {@link com.example.auth.domain.tenant.TenantContext} that misclassified new
     * B2C tenants (e.g. {@code ecommerce}) as {@code B2B_ENTERPRISE}. The login /
     * refresh / social-callback paths consume this (via
     * {@link com.example.auth.infrastructure.tenant.TenantTypeResolver}) to populate
     * the signed {@code tenant_type} claim accurately.</p>
     *
     * @param tenantId the tenant whose type to resolve
     * @return the tenant_type string, or empty if the tenant does not exist (404)
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException
     *         if account-service is down (5xx / circuit-open / timeout / IO)
     */
    Optional<String> getTenantType(String tenantId);

    /**
     * Looks up a tenant's registry record — {@code tenant_type} AND {@code status}.
     *
     * <p>TASK-BE-581. {@link #getTenantType(String)} answers only "what type", discarding
     * the {@code status} field that {@code GET /internal/tenants/{tenantId}} already
     * returns. The browser signup surface needs BOTH, because the predicate that actually
     * decides whether an account can be born in a tenant is account-service's
     * {@code ActiveTenantGuard} — <b>row exists AND status is ACTIVE</b>. A caller that
     * sees only existence reports a suspended tenant as signup-capable and the user then
     * hits a 403 from the surface that just offered them the form.</p>
     *
     * <p>Same failure mapping as {@link #getTenantType(String)}: a 404 is "no such tenant"
     * (empty), and any NON-404 4xx / 5xx / timeout is an outage, not an answer.</p>
     *
     * @param tenantId the tenant to look up
     * @return the tenant record, or empty if the tenant does not exist (404)
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException
     *         if account-service is down (5xx / circuit-open / timeout / IO)
     */
    Optional<TenantLookupResult> getTenant(String tenantId);

    /**
     * A tenant registry record as account-service reports it.
     *
     * @param tenantType the authoritative {@code tenant_type} (e.g. {@code B2C})
     * @param status     the tenant lifecycle status (e.g. {@code ACTIVE}, {@code SUSPENDED})
     */
    record TenantLookupResult(String tenantType, String status) {
    }
}
