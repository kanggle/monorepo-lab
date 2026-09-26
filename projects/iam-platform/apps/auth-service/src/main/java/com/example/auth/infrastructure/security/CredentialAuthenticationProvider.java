package com.example.auth.infrastructure.security;

import com.example.auth.application.AccountStatusRule;
import com.example.auth.application.LoginEventRecorder;
import com.example.auth.application.LoginHashes;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantTypePort;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.presentation.SessionContexts;
import com.example.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-BE-309 — Spring Security {@link AuthenticationProvider} that bridges
 * the HTML form-login flow ({@link com.example.auth.infrastructure.config.WebLoginSecurityConfig})
 * to the existing credential storage and password hasher.
 *
 * <p>This path does <b>not</b> reuse {@link com.example.auth.application.LoginUseCase}
 * (which has had no caller since TASK-BE-398 retired {@code POST /api/auth/login}).
 *
 * <p><b>Login events (TASK-BE-599).</b> This provider is the only password-verification path,
 * so it is where {@code auth.login.attempted/failed/succeeded} are produced (via
 * {@link LoginEventRecorder}) — the input security-service's VELOCITY / GEO_ANOMALY rules
 * need. No device session is registered and no {@code auth.session.created} is emitted
 * (owner decision, AC-0 ⓒ withdrawn: without a browser device fingerprint every login would
 * be a "new device"). The provider is the seam because it is the only point that knows
 * the resolved credential on a FAILED attempt: a Spring failure handler or
 * {@code AuthenticationFailureEvent} listener sees only the exception, and VelocityRule
 * ignores failures without an {@code accountId}. Rate limiting is deliberately NOT applied
 * (owner decision, TASK-BE-599 AC-0 ⓑ declined: shared demo accounts must not start being
 * blocked after N failures). Every recorder call is telemetry — a failure is logged and the
 * login outcome is unchanged ({@link #telemetry}).
 *
 * <p><b>Account status (TASK-BE-600).</b> A LOCKED / DORMANT / DELETED account is refused
 * by the same {@link AccountStatusRule} the social callback uses. Before BE-600 this provider
 * never looked at status, so a locked account (admin lock or security-service auto-lock)
 * still signed in with its password while the social path refused it. Three decisions:
 * <ul>
 *   <li><b>Response shape (owner decision, AC-1 ⓐ)</b> — a status rejection is EXACTLY a
 *       wrong password: {@link BadCredentialsException}{@code ("Invalid credentials")} →
 *       {@code /login?error}. Saying "locked" only when the password was right would turn a
 *       locked account into a password-confirmation oracle.</li>
 *   <li><b>Order</b> — status is looked up BEFORE the password is verified, and the password
 *       is verified regardless of the status. Every found credential therefore pays the same
 *       two costs (one account-service call, one hash verification) whatever its status and
 *       whatever the password, so neither the response nor its timing separates "locked +
 *       right password" from "locked + wrong password", nor a locked account from an active
 *       one given a wrong password. Checking after verification would add the account-service
 *       round trip only when the password was right (a timing oracle); rejecting on status
 *       before verification would skip the hash for non-ACTIVE accounts (a status oracle).
 *       When both the password and the status are wrong, the event says
 *       {@code CREDENTIALS_INVALID}, so password failures feed VelocityRule exactly as before.</li>
 *   <li><b>Lookup failure (owner decision, AC-2)</b> — fail CLOSED: an account-service
 *       failure (5xx / timeout / open circuit / non-404 4xx / unreadable body) rejects the
 *       login as {@link AuthenticationServiceException}. A 404 is not a failure: no account
 *       record exists for the credential (every console operator in tenant {@code iam}), so
 *       the rule has nothing to apply to.</li>
 * </ul>
 *
 * <p><b>Tenant resolution (TASK-BE-507 D1-a, narrowed by TASK-BE-604 D).</b> The lookup is
 * scoped to the tenant of the OIDC client the user is logging in through
 * ({@link SavedRequestTenantResolver}, the same source the social path uses). A scoped miss
 * falls back to the cross-tenant lookup only when that client is the console
 * ({@link TenantContext#CONSOLE_TENANT_ID}); through any other client it is refused like a
 * wrong password. BE-507 kept the fallback for every client to carry pre-BE-507
 * {@code fan-platform} shoppers into web-store; TASK-MONO-386 measured that population at
 * zero, and BE-604 AC-0 showed the fallback minted cross-tenant sessions nobody could use.
 * The console keeps it because ADR-MONO-044 D5 operators have no {@code iam} credential.
 * See {@link #resolveCredential} for the whole table.
 *
 * <p>The cross-tenant lookup keeps its fail-closed ambiguity guard (an email in two tenants
 * → {@link BadCredentialsException}). It surfaces for a caller with no initiating client and
 * for a console login whose email exists in two or more non-console tenants.
 *
 * <p>The resolved tenant is published as
 * {@code Authentication.getDetails() = Map.of("tenant_id", ..., "tenant_type", ...)}
 * so that the existing
 * {@link com.example.auth.infrastructure.oauth2.TenantClaimTokenCustomizer}
 * picks it up via its first {@code extractTenantAttribute(principal, ...)}
 * path — no customizer code change required.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialAuthenticationProvider implements AuthenticationProvider {

    private final CredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final TenantTypePort tenantTypePort;
    private final SavedRequestTenantResolver savedRequestTenantResolver;
    private final LoginEventRecorder loginEventRecorder;
    private final AccountServicePort accountServicePort;

    /** {@code auth.login.failed.failureReason} values this path produces (auth-events.md enum). */
    static final String REASON_CREDENTIALS_INVALID = "CREDENTIALS_INVALID";
    static final String REASON_TENANT_AMBIGUOUS = "LOGIN_TENANT_AMBIGUOUS";

    /**
     * TASK-BE-600: the status rejections that have a {@code failureReason} in the event
     * contract. {@link AccountStatusRule#CODE_UNKNOWN} is deliberately absent.
     */
    private static final Set<String> CONTRACT_STATUS_REASONS = AccountStatusRule.EVENT_FAILURE_REASONS;

    /**
     * TASK-BE-600 — the account's status from account-service, looked up in the account's own
     * tenant. Empty means account-service answered 404: there is no account record to apply
     * the status rule to — which is the designed state of every console operator credential
     * (tenant {@code iam} has no {@code accounts} rows). Any failed lookup fails CLOSED
     * (owner decision, AC-2) as an {@link AuthenticationServiceException}, which the form
     * renders as the same {@code /login?error} as a wrong password.
     */
    private Optional<String> lookupAccountStatus(String accountId, String tenantId) {
        try {
            return accountServicePort.getAccountStatus(accountId, tenantId)
                    .map(AccountStatusLookupResult::accountStatus);
        } catch (AccountServiceUnavailableException e) {
            log.warn("form-login: account status lookup failed — failing closed");
            throw new AuthenticationServiceException("Account status service is unavailable", e);
        }
    }

    /**
     * Applies the shared {@link AccountStatusRule} and returns the rejection code, or
     * {@code null} when the status may sign in.
     */
    private static String statusRejection(String status) {
        try {
            AccountStatusRule.enforce(status);
            return null;
        } catch (AccountLockedException e) {
            return AccountStatusRule.REASON_LOCKED;
        } catch (AccountStatusException e) {
            return e.getErrorCode();
        }
    }

    /**
     * Outcome of the credential lookup: either the credential, or the failure reason that the
     * {@code auth.login.failed} event carries. The caller throws the same
     * {@link BadCredentialsException} for both failure reasons, so the HTTP response
     * ({@code /login?error}) never reveals which one happened.
     */
    private record Lookup(Credential credential, String failureReason) {
        static Lookup found(Credential credential) {
            return new Lookup(credential, null);
        }

        static Lookup failed(String failureReason) {
            return new Lookup(null, failureReason);
        }
    }

    /**
     * Resolves the credential a form login is for — "which accounts may log into which
     * clients" (multi-tenancy.md § 로그인 가능한 계정과 client).
     *
     * <ol>
     *   <li><b>Scoped</b> (TASK-BE-507 D1-a) — the credential in the tenant of the initiating
     *       OIDC client. A hit ends the lookup whatever the client.</li>
     *   <li><b>Scoped miss, console client</b> ({@link TenantContext#CONSOLE_TENANT_ID}) —
     *       the cross-tenant lookup below. An operator self-onboarded under ADR-MONO-044 D5 has
     *       no {@code iam} credential: operator {@code oidc_subject} = consumer
     *       {@code account_id}, so the console is reachable for them only through this.</li>
     *   <li><b>Scoped miss, any other client</b> — refused as {@code CREDENTIALS_INVALID}, the
     *       same response as a wrong password (TASK-BE-604, owner decision D, 2026-09-26).
     *       BE-507 kept the fallback here for pre-BE-507 {@code fan-platform} shoppers;
     *       TASK-MONO-386 measured that population at zero, and the session it produced was
     *       unusable anyway (no seeded role on the storefront, gateway tenant gate). Such a
     *       person signs up in the client's tenant instead ({@code (tenant_id, email)} unique).</li>
     *   <li><b>No initiating client</b> ({@code clientTenant == null}: no saved
     *       {@code /oauth2/authorize}, or no request bound) — the cross-tenant lookup, unchanged:
     *       one match wins, several fail closed as {@code LOGIN_TENANT_AMBIGUOUS}.</li>
     * </ol>
     */
    private Lookup resolveCredential(String email, String clientTenant) {
        if (clientTenant != null) {
            Optional<Credential> scoped = credentialRepository.findByTenantIdAndEmail(clientTenant, email);
            if (scoped.isPresent()) {
                return Lookup.found(scoped.get());
            }
            if (!TenantContext.CONSOLE_TENANT_ID.equals(clientTenant)) {
                log.debug("form-login scoped lookup miss in consumer tenant={} — no cross-tenant "
                        + "fallback for a consumer client (TASK-BE-604)", clientTenant);
                return Lookup.failed(REASON_CREDENTIALS_INVALID);
            }
            log.debug("form-login scoped lookup miss in the console tenant — falling back to "
                    + "cross-tenant (an operator whose credential lives in a consumer tenant)");
        }

        List<Credential> matches = credentialRepository.findAllByEmail(email);
        if (matches.isEmpty()) {
            log.debug("form-login credential lookup miss for emailHash=<redacted>");
            return Lookup.failed(REASON_CREDENTIALS_INVALID);
        }
        if (matches.size() > 1) {
            // Reachable without an initiating client, and through the console client when the
            // email exists in two or more non-console tenants. Fail-closed either way.
            log.warn("form-login tenant ambiguity: email matches {} tenants and the initiating "
                    + "client does not decide between them — failing closed", matches.size());
            return Lookup.failed(REASON_TENANT_AMBIGUOUS);
        }
        return Lookup.found(matches.get(0));
    }

    /**
     * The request's session context (client IP, User-Agent, device fingerprint, geo), derived
     * exactly as every other auth-service entry point derives it ({@link SessionContexts}) —
     * including its use of {@code getRemoteAddr()}, which behind a reverse proxy is the proxy's
     * address unless forwarded headers are honoured (TASK-BE-599 AC-4). With no request bound
     * (a non-web caller) every field is unknown.
     */
    private static SessionContext currentSessionContext() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return SessionContexts.fromRequest(servletAttrs.getRequest());
        }
        return new SessionContext(null, null, null);
    }

    /**
     * Runs a login-telemetry side effect. Any failure (e.g. the outbox write) is
     * logged and swallowed: telemetry must never turn a correct password into a failed login,
     * nor a wrong password into a 500 (TASK-BE-599 — owner decision recorded on AC-0).
     */
    private static void telemetry(String what, Runnable sideEffect) {
        try {
            sideEffect.run();
        } catch (RuntimeException e) {
            log.warn("form-login telemetry '{}' failed — login outcome unaffected", what, e);
        }
    }

    /**
     * The tenant of the OIDC client whose {@code /oauth2/authorize} request sent the user to
     * the login form, or {@code null} when there is no request context / no saved request
     * (e.g. a direct visit to {@code /login}) — in which case the caller keeps the legacy
     * cross-tenant behaviour.
     *
     * <p>TASK-BE-604: read through {@link SavedRequestTenantResolver#initiatingClientTenant},
     * not {@code resolve(...).tenantId()}. The latter substitutes {@code fan-platform} when
     * there is no initiating client, so the {@code null} this javadoc promised was reachable
     * only with no request bound — a direct visit to {@code /login} was scoped to
     * {@code fan-platform} and then fell back. With the fallback now limited to the console,
     * that substitute would have turned "no client" into "a consumer client".
     */
    private String resolveClientTenant() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return savedRequestTenantResolver
                .initiatingClientTenant(servletAttrs.getRequest(), servletAttrs.getResponse())
                .orElse(null);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        Object rawCredentials = authentication.getCredentials();
        if (email == null || email.isBlank() || !(rawCredentials instanceof String password)
                || password.isBlank()) {
            // Spring Security normalizes missing principal/credentials to BadCredentials
            // for the user-visible message; avoid leaking which one is missing.
            // A malformed submission, not a login attempt against an identity: no events.
            throw new BadCredentialsException("Invalid credentials");
        }

        SessionContext ctx = currentSessionContext();
        String emailHash = LoginHashes.emailHash(email);
        String clientTenant = resolveClientTenant();

        Lookup lookup = resolveCredential(email, clientTenant);
        if (lookup.credential() == null) {
            // No single identity: accountId=null (contract), tenant = the login's own tenant
            // context. Same exception as a wrong password — no enumeration via the response.
            String contextTenant = clientTenant != null ? clientTenant : TenantContext.DEFAULT_TENANT_ID;
            telemetry("attempted", () ->
                    loginEventRecorder.recordAttempted(null, emailHash, contextTenant, ctx));
            telemetry("failed", () -> loginEventRecorder.recordFailed(
                    null, emailHash, contextTenant, lookup.failureReason(), ctx));
            throw new BadCredentialsException("Invalid credentials");
        }

        Credential credential = lookup.credential();
        String accountId = credential.getAccountId();
        // The ACCOUNT's own tenant — not the client tenant — even when the credential came from
        // the cross-tenant fallback: security-service keys its per-tenant counters on it (BE-259).
        String tenantId = Optional.ofNullable(credential.getTenantId())
                .filter(s -> !s.isBlank())
                .orElse(TenantContext.DEFAULT_TENANT_ID);

        telemetry("attempted", () ->
                loginEventRecorder.recordAttempted(accountId, emailHash, tenantId, ctx));

        // TASK-BE-600: account status is looked up BEFORE the password is verified, and the
        // password is verified whatever the status turns out to be — see the class javadoc
        // ("Account status") for why this order leaks neither the password nor the status.
        Optional<String> accountStatus = lookupAccountStatus(accountId, tenantId);

        if (!passwordHasher.verify(password, credential.getCredentialHash())) {
            log.debug("form-login password verification failed for emailHash=<redacted>");
            // accountId MUST be present: VelocityRule ignores failures without one.
            telemetry("failed", () -> loginEventRecorder.recordFailed(
                    accountId, emailHash, tenantId, REASON_CREDENTIALS_INVALID, ctx));
            throw new BadCredentialsException("Invalid credentials");
        }

        String rejection = accountStatus.map(CredentialAuthenticationProvider::statusRejection)
                .orElse(null);
        if (rejection != null) {
            log.info("form-login rejected: account status is not ACTIVE (reason={})", rejection);
            if (CONTRACT_STATUS_REASONS.contains(rejection)) {
                telemetry("failed", () -> loginEventRecorder.recordFailed(
                        accountId, emailHash, tenantId, rejection, ctx));
            } else {
                // A status outside account-service's enum: rejected all the same, but there is
                // no failureReason in the event contract to carry it, and inventing one would
                // break the consumer's enum. The attempted event stands alone, as on an outage.
                log.warn("form-login: account-service returned a status outside the contract "
                        + "enum — rejected, no auth.login.failed emitted");
            }
            // Owner decision (AC-1 ⓐ): EXACTLY the wrong-password outcome — same exception,
            // same message, so /login?error and "Invalid email or password." are identical.
            throw new BadCredentialsException("Invalid credentials");
        }

        // TASK-BE-407: authoritative tenant_type from account-service (cached).
        // AC-5: an account-service outage must surface as an AuthenticationException
        // (AuthenticationServiceException) rather than leaking a raw RuntimeException
        // out of the AuthenticationProvider, which the ProviderManager would otherwise
        // propagate to the filter chain as a 500. Wrapping maps the infra failure to a
        // clean authentication-boundary error.
        String tenantType;
        try {
            tenantType = tenantTypePort.resolve(tenantId);
        } catch (AccountServiceUnavailableException e) {
            throw new AuthenticationServiceException(
                    "Tenant metadata service is unavailable", e);
        }

        // The principal is the email (Spring Security default for username/password
        // flows). Tenant context is published via `details` so that
        // TenantClaimTokenCustomizer picks it up via its existing
        // `principal.getDetails() map` path (NO customizer change required).
        //
        // The details map MUST be a `HashMap` (not `Map.of(...)`) because SAS's
        // JdbcOAuth2AuthorizationService serializes the Authentication via
        // Jackson with a strict allowlist (SecurityJackson2Modules) when it
        // persists the OAuth2Authorization to the DB at /oauth2/authorize time.
        // `Map.of(...)` returns `java.util.ImmutableCollections$MapN`, which is
        // NOT on the allowlist, and the subsequent /oauth2/token round-trip
        // fails with `IllegalArgumentException: not in the allowlist`.
        // `HashMap` is on the allowlist via Spring Security's stock mixins.
        // TASK-MONO-263 (ADR-032 D5 step 4): the account_type detail is no longer
        // published — the claim is removed entirely. The roles claim (seeded by
        // RoleSeedPolicy on platform, BE-369) is the sole authorization surface.
        // TASK-BE-577: the email is published as a detail rather than read back off
        // the principal name at issuance time. Both are the same string today, but
        // the principal name is a Spring Security convention that a producer may
        // change without any compile error — which is the exact drift this key class
        // exists to prevent (see PrincipalDetailKeys' javadoc).
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, tenantId);
        details.put(PrincipalDetailKeys.TENANT_TYPE, tenantType);
        details.put(PrincipalDetailKeys.ACCOUNT_ID, credential.getAccountId());
        details.put(PrincipalDetailKeys.EMAIL, credential.getEmail());

        UsernamePasswordAuthenticationToken authenticated =
                new UsernamePasswordAuthenticationToken(
                        credential.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authenticated.setDetails(details);

        // Last, once every step that can still reject the login has passed — so a
        // succeeded event is never recorded for a login that then fails.
        telemetry("succeeded", () -> loginEventRecorder.recordSucceeded(accountId, tenantId, ctx));
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
