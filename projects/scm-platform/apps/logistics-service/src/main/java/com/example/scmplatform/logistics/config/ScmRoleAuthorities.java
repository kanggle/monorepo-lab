package com.example.scmplatform.logistics.config;

import com.example.security.servlet.actor.ActorClaims;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * TASK-MONO-792 (ADR-MONO-083 P5) — lifts the verified JWT {@code roles} (or {@code role}) claim into
 * {@code ROLE_<name>} authorities so the filter chain's {@code hasRole(...)} business-role gates can read
 * them. The claim lifting itself is the shared {@link ActorClaims} (both wire forms, ADR-MONO-058 § D1);
 * the principal stays the {@code Jwt} — the token is still a {@code JwtAuthenticationToken}, so
 * {@code TenantClaimEnforcer} keeps working.
 *
 * <p>{@code SCOPE_*} authorities are no longer produced: nothing in this service reads them, and the
 * {@code scm.read / scm.write} scopes deliberately open no business API (scm iam-integration.md § 업무 역할).
 */
final class ScmRoleAuthorities {

    private ScmRoleAuthorities() {
    }

    static JwtAuthenticationConverter converter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> ActorClaims.from(jwt).authorities());
        return converter;
    }
}
