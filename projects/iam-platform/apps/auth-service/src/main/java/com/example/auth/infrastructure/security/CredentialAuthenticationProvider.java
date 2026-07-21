package com.example.auth.infrastructure.security;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.TenantTypePort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.tenant.TenantContext;
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
 * <p>This is intentionally a thin path that does <b>not</b> reuse the full
 * {@link com.example.auth.application.LoginUseCase}. The full use case also
 * applies rate-limiting, publishes login-attempt/success/failure events, and
 * registers a device session — side effects that the JSON
 * {@code POST /api/auth/login} endpoint owns and that are out of scope for
 * the v1 form-login surface. Future enhancement work can promote the
 * credential-verify portion of {@link com.example.auth.application.LoginUseCase}
 * to a shared service and call it from both paths.
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

    /**
     * TASK-BE-507 (D1-a): resolve the credential by the tenant of the initiating OIDC client
     * first; fall back to the pre-BE-507 cross-tenant lookup when that misses.
     *
     * <p>The fallback is what makes this safe to ship without a data migration: every account
     * that exists today is {@code fan-platform}, so an ecommerce shopper logging in through the
     * web-store client misses the scoped lookup and is found by the fallback — byte-identical to
     * today. New shoppers, born {@code ecommerce}, hit the scoped lookup instead.
     */
    private Credential resolveCredential(String email) {
        String clientTenant = resolveClientTenant();
        if (clientTenant != null) {
            Optional<Credential> scoped = credentialRepository.findByTenantIdAndEmail(clientTenant, email);
            if (scoped.isPresent()) {
                return scoped.get();
            }
            log.debug("form-login scoped lookup miss in tenant={} — falling back to cross-tenant "
                    + "(a pre-BE-507 account lives in another tenant)", clientTenant);
        }

        List<Credential> matches = credentialRepository.findAllByEmail(email);
        if (matches.isEmpty()) {
            log.debug("form-login credential lookup miss for emailHash=<redacted>");
            throw new BadCredentialsException("Invalid credentials");
        }
        if (matches.size() > 1) {
            // Only reachable without an initiating client (no saved authorize request): with one,
            // the scoped lookup above already disambiguated. Still fail-closed.
            log.warn("form-login tenant ambiguity: email matches {} tenants and no initiating "
                    + "client tenant is available — failing closed", matches.size());
            throw new BadCredentialsException("Invalid credentials");
        }
        return matches.get(0);
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
            throw new BadCredentialsException("Invalid credentials");
        }

        Credential credential = resolveCredential(email);
        if (!passwordHasher.verify(password, credential.getCredentialHash())) {
            log.debug("form-login password verification failed for emailHash=<redacted>");
            throw new BadCredentialsException("Invalid credentials");
        }

        String tenantId = Optional.ofNullable(credential.getTenantId())
                .filter(s -> !s.isBlank())
                .orElse(TenantContext.DEFAULT_TENANT_ID);
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
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, tenantId);
        details.put(PrincipalDetailKeys.TENANT_TYPE, tenantType);
        details.put(PrincipalDetailKeys.ACCOUNT_ID, credential.getAccountId());

        UsernamePasswordAuthenticationToken authenticated =
                new UsernamePasswordAuthenticationToken(
                        credential.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authenticated.setDetails(details);
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
