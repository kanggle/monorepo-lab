package com.example.auth.infrastructure.security;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.tenant.TenantContext;
import com.example.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

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
 * <p>Tenant resolution mirrors {@link com.example.auth.application.LoginUseCase}'s
 * TASK-BE-229 logic in simplified form. v1 single-tenant assumption: if an
 * email matches multiple tenants the login fails closed with
 * {@link BadCredentialsException} (user sees "invalid credentials" rather
 * than a tenant-chooser UI). A tenant chooser is a separate future task.
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

        // Cross-tenant lookup — v1 fails closed on ambiguity. A tenant-chooser
        // UI is a separate future task; for now ambiguity manifests as
        // "invalid credentials" from the user's perspective.
        List<Credential> matches = credentialRepository.findAllByEmail(email);
        if (matches.isEmpty()) {
            log.debug("form-login credential lookup miss for emailHash=<redacted>");
            throw new BadCredentialsException("Invalid credentials");
        }
        if (matches.size() > 1) {
            log.warn("form-login tenant ambiguity: email matches {} tenants — failing closed "
                    + "(tenant chooser UI is a future task)", matches.size());
            throw new BadCredentialsException("Invalid credentials");
        }

        Credential credential = matches.get(0);
        if (!passwordHasher.verify(password, credential.getCredentialHash())) {
            log.debug("form-login password verification failed for emailHash=<redacted>");
            throw new BadCredentialsException("Invalid credentials");
        }

        String tenantId = Optional.ofNullable(credential.getTenantId())
                .filter(s -> !s.isBlank())
                .orElse(TenantContext.DEFAULT_TENANT_ID);
        String tenantType = TenantContext.resolveTenantType(tenantId);

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
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", tenantId);
        details.put("tenant_type", tenantType);
        details.put("account_id", credential.getAccountId());

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
