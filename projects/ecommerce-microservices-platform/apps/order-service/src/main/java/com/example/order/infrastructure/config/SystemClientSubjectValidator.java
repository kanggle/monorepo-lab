package com.example.order.infrastructure.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;

/**
 * Validates that a JWT presented on {@code /api/internal/**} was minted for the reserved
 * IAM system client(s) — i.e. its {@code sub} is one of the allow-listed
 * {@code client_credentials} client-ids (TASK-BE-505).
 *
 * <h2>Why this exists</h2>
 * <p>The {@code order-confirm-paid-stale.md} contract promises the endpoint "NEVER executes
 * the sweep without a valid <b>system</b> credential" (client {@code ecommerce-internal-services-client}).
 * The prior chain only pinned signature + timestamps + issuer + (optional) audience and then
 * did {@code .anyRequest().authenticated()} — but every ecommerce token (system AND ordinary
 * CUSTOMER access tokens) is minted by the <b>same</b> Spring Authorization Server issuer, so
 * "authenticated" did not distinguish a system credential from a user token. A valid CUSTOMER
 * token (or any other client on the same issuer) therefore passed, contradicting the contract
 * and exposing the internal mass-confirm + cross-tenant operator-cancel endpoints to any
 * internal-network holder of an ordinary token.
 *
 * <h2>The discriminator</h2>
 * <p>Per {@code auth-service}'s {@code TenantClaimTokenCustomizer}, a {@code client_credentials}
 * access token's {@code sub} is the client-id (the framework default — the customizer only
 * overrides {@code sub} to the account UUID on the {@code authorization_code}/{@code refresh_token}
 * paths, never for {@code client_credentials}), and it carries no {@code roles} claim. An
 * ordinary CUSTOMER token has {@code sub = <account UUID>}. So pinning {@code sub} to the
 * reserved client-id is a positive, fail-closed allow-list: a user token (sub = UUID) and any
 * other platform's client (sub = its own client-id) are rejected with 401.
 *
 * <p>Runs as a decoder-level {@link OAuth2TokenValidator} (alongside {@link AudienceValidator}),
 * so a wrong-subject token fails verification → {@code 401 UNAUTHORIZED} via the existing
 * {@code OrderSecurityConfig} entry point, matching the contract's fail-closed 401 framing.
 */
public class SystemClientSubjectValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error NOT_SYSTEM_CLIENT = new OAuth2Error(
            "invalid_token",
            "The token subject is not an allow-listed internal system client",
            null);

    private final Set<String> allowedClientIds;

    public SystemClientSubjectValidator(Set<String> allowedClientIds) {
        this.allowedClientIds = Set.copyOf(allowedClientIds);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (allowedClientIds.isEmpty()) {
            // Fail-closed by construction: an empty allow-list is a wiring error, not a
            // reason to admit everyone. Reject so a mis-configuration surfaces as 401
            // rather than silently reopening the gap.
            return OAuth2TokenValidatorResult.failure(NOT_SYSTEM_CLIENT);
        }
        String subject = token.getSubject();
        if (subject != null && allowedClientIds.contains(subject)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(NOT_SYSTEM_CLIENT);
    }
}
