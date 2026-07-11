package com.example.apigateway.security;

/**
 * Error codes that cross the library / service boundary.
 *
 * <p>Exists to break exactly one dependency: the shared {@code SecurityConfig} must
 * recognise a cross-tenant rejection in order to map it to 403 {@code TENANT_FORBIDDEN}
 * instead of the default 401, but the validator that <em>raises</em> that code
 * ({@code TenantClaimValidator}) is still per-domain — its gate policy differs across
 * gateways and it moves to the library only in ADR-MONO-048 D7 step 2.
 *
 * <p>Rather than have the library reach into a service class, or have the two sides
 * each hard-code the literal {@code "tenant_mismatch"} (which is precisely the
 * duplicate-definition pattern this library exists to end), the constant lives here and
 * both sides point at it. Each domain's {@code TenantClaimValidator} keeps its public
 * {@code ERROR_CODE_TENANT_MISMATCH} field — with the same value it always had — by
 * delegating to this one.
 *
 * <p>When step 2 moves {@code TenantClaimValidator} into the library, the per-domain
 * constants disappear and this class becomes its natural home.
 */
public final class GatewayErrorCodes {

    /**
     * Raised by a tenant-claim validator when a token's tenant is not admissible at
     * this edge. The gateway maps it to 403 {@code TENANT_FORBIDDEN} — not 401 —
     * because a cross-tenant token is signature-valid: telling the client to
     * re-authenticate would be a lie, and it would loop on token refresh forever.
     */
    public static final String TENANT_MISMATCH = "tenant_mismatch";

    private GatewayErrorCodes() {
    }
}
