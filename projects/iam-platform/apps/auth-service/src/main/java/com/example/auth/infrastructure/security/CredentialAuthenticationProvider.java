package com.example.auth.infrastructure.security;

import com.example.auth.application.LoginEventRecorder;
import com.example.auth.application.LoginHashes;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.TenantTypePort;
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

/**
 * TASK-BE-309 — Spring Security {@link AuthenticationProvider} that bridges
 * the HTML form-login flow ({@link com.example.auth.infrastructure.config.WebLoginSecurityConfig})
 * to the existing credential storage and password hasher.
 *
 * <p>This path does <b>not</b> reuse {@link com.example.auth.application.LoginUseCase}
 * (which has had no caller since TASK-BE-398 retired {@code POST /api/auth/login}).
 *
 * <p><b>Login events + device session (TASK-BE-599).</b> This provider is the only
 * password-verification path, so it is where {@code auth.login.attempted/failed/succeeded}
 * and {@code auth.session.created} are produced (via
 * {@link LoginEventRecorder}) — the input security-service's VELOCITY / DEVICE_CHANGE /
 * GEO_ANOMALY rules need. The provider is the seam because it is the only point that knows
 * the resolved credential on a FAILED attempt: a Spring failure handler or
 * {@code AuthenticationFailureEvent} listener sees only the exception, and VelocityRule
 * ignores failures without an {@code accountId}. Rate limiting is deliberately NOT applied
 * (owner decision, TASK-BE-599 AC-0 ⓑ declined: shared demo accounts must not start being
 * blocked after N failures). Every recorder call is telemetry — a failure is logged and the
 * login outcome is unchanged ({@link #telemetry}).
 *
 * <p><b>Tenant resolution (TASK-BE-507, D1-a).</b> The lookup is scoped to the tenant of the
 * OIDC client the user is logging in through ({@link SavedRequestTenantResolver}, the same
 * source the social path uses), falling back to the pre-BE-507 cross-tenant lookup when the
 * scoped lookup finds nothing — which is what keeps every account created before BE-507
 * (all of them {@code fan-platform}, including ecommerce shoppers) logging in exactly as
 * before. Without the fallback, scoping alone would lock out every existing shopper the
 * moment web-store started asking for {@code ecommerce}.
 *
 * <p>The cross-tenant fallback keeps its fail-closed ambiguity guard (an email in two tenants
 * → {@link BadCredentialsException}) — but BE-507 makes that unreachable for the case it was
 * written for: once the same email exists in fan-platform AND ecommerce, the scoped lookup
 * resolves it by client and never reaches the fallback. Ambiguity now only surfaces for a
 * caller with no initiating client at all.
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

    /** {@code auth.login.failed.failureReason} values this path produces (auth-events.md enum). */
    static final String REASON_CREDENTIALS_INVALID = "CREDENTIALS_INVALID";
    static final String REASON_TENANT_AMBIGUOUS = "LOGIN_TENANT_AMBIGUOUS";

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
     * TASK-BE-507 (D1-a): resolve the credential by the tenant of the initiating OIDC client
     * first; fall back to the pre-BE-507 cross-tenant lookup when that misses.
     *
     * <p>The fallback is what makes this safe to ship without a data migration: every account
     * that exists today is {@code fan-platform}, so an ecommerce shopper logging in through the
     * web-store client misses the scoped lookup and is found by the fallback — byte-identical to
     * today. New shoppers, born {@code ecommerce}, hit the scoped lookup instead.
     */
    private Lookup resolveCredential(String email, String clientTenant) {
        if (clientTenant != null) {
            Optional<Credential> scoped = credentialRepository.findByTenantIdAndEmail(clientTenant, email);
            if (scoped.isPresent()) {
                return Lookup.found(scoped.get());
            }
            log.debug("form-login scoped lookup miss in tenant={} — falling back to cross-tenant "
                    + "(a pre-BE-507 account lives in another tenant)", clientTenant);
        }

        List<Credential> matches = credentialRepository.findAllByEmail(email);
        if (matches.isEmpty()) {
            log.debug("form-login credential lookup miss for emailHash=<redacted>");
            return Lookup.failed(REASON_CREDENTIALS_INVALID);
        }
        if (matches.size() > 1) {
            // Only reachable without an initiating client (no saved authorize request): with one,
            // the scoped lookup above already disambiguated. Still fail-closed.
            log.warn("form-login tenant ambiguity: email matches {} tenants and no initiating "
                    + "client tenant is available — failing closed", matches.size());
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
     * Runs a login-telemetry side effect. Any failure (outbox write, device-session upsert) is
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
     */
    private String resolveClientTenant() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return savedRequestTenantResolver
                .resolve(servletAttrs.getRequest(), servletAttrs.getResponse())
                .tenantId();
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

        if (!passwordHasher.verify(password, credential.getCredentialHash())) {
            log.debug("form-login password verification failed for emailHash=<redacted>");
            // accountId MUST be present: VelocityRule ignores failures without one.
            telemetry("failed", () -> loginEventRecorder.recordFailed(
                    accountId, emailHash, tenantId, REASON_CREDENTIALS_INVALID, ctx));
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
        // succeeded event / device session is never recorded for a login that then fails.
        telemetry("succeeded", () -> loginEventRecorder.recordSucceeded(accountId, tenantId, ctx));
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
