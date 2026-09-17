package com.example.apigateway.security;

import com.example.security.oauth2.TenantClaimValidator;

/**
 * Error codes that cross the library / service boundary.
 *
 * <p><strong>This class no longer owns its value — and that is the point.</strong> It was
 * written to break exactly one dependency: the shared {@code SecurityConfig} must recognise a
 * cross-tenant rejection to map it to 403 {@code TENANT_FORBIDDEN} rather than the default
 * 401, but the validator that <em>raises</em> that code was still per-domain. Rather than have
 * both sides hard-code the literal {@code "tenant_mismatch"} — precisely the duplicate-definition
 * pattern this library exists to end — the constant lived here and both sides pointed at it
 * (TASK-MONO-351).
 *
 * <p>This class's own Javadoc predicted the end of that arrangement: <em>"when step 2 moves
 * {@code TenantClaimValidator} into the library … this class becomes its natural home."</em>
 * **The prediction was half right.** The validator did move — but to
 * {@code libs/java-security}, not here, because eighteen servlet services need it and none of
 * them may see WebFlux (ADR-MONO-049 § D1). And {@code java-gateway} <em>depends on</em>
 * {@code java-security}, so this class cannot be the validator's home without the two modules
 * forming a cycle.
 *
 * <p><strong>So the arrow reversed instead.</strong> {@link TenantClaimValidator} owns the
 * literal; this constant points at it. One definition, both sides pointing at it — MONO-351's
 * property is intact; only its direction changed. (ADR-MONO-049 § D5-1.)
 */
public final class GatewayErrorCodes {

    /**
     * Raised by a tenant-claim validator when a token's tenant is not admissible at
     * this edge. The gateway maps it to 403 {@code TENANT_FORBIDDEN} — not 401 —
     * because a cross-tenant token is signature-valid: telling the client to
     * re-authenticate would be a lie, and it would loop on token refresh forever.
     *
     * <p>Delegates to {@link TenantClaimValidator#ERROR_CODE_TENANT_MISMATCH}, which is the
     * definition. {@code GatewayErrorCodesTest} asserts the two agree <em>and</em> that the
     * wire value is still {@code "tenant_mismatch"} — a rename on either side is a wire
     * break, and the test is what says so.
     */
    public static final String TENANT_MISMATCH = TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH;

    /**
     * Raised by {@link AllowedAudiencesValidator} in {@link AudienceMode#ENFORCE} when a token's
     * {@code aud} shares no member with the gateway's audience allowlist. Mapped to 403
     * {@link #AUDIENCE_FORBIDDEN} for the same reason as {@link #TENANT_MISMATCH}: the token is
     * signature-valid, and re-authenticating cannot change the client it was issued to.
     * (TASK-MONO-696; {@code jwt-standard-claims.md} § Error Handling.)
     */
    public static final String AUDIENCE_MISMATCH = AllowedAudiencesValidator.ERROR_CODE_AUDIENCE_MISMATCH;

    /**
     * The response error code for an audience rejection.
     *
     * <p><strong>Still a proposal.</strong> The contract names {@code AUDIENCE_FORBIDDEN} as a
     * proposal parallel to {@code TENANT_FORBIDDEN} and leaves it to be settled by the change that
     * turns rejection on. Every gateway ships in shadow mode, so no client can observe this value
     * yet; confirming (or renaming) it is part of that switch.
     */
    public static final String AUDIENCE_FORBIDDEN = "AUDIENCE_FORBIDDEN";

    private GatewayErrorCodes() {
    }
}
