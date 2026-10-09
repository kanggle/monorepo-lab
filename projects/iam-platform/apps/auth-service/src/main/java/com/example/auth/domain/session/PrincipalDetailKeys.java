package com.example.auth.domain.session;

/**
 * Transport keys for the authenticated principal's tenant/account attributes that
 * login carries through Spring Security's {@code Authentication.getDetails()} map.
 *
 * <p>Two producers populate this map — {@code CredentialAuthenticationProvider}
 * (form-login bridge) and {@code SocialLoginBrowserController.establishSession}
 * (social bridge) — and one consumer reads it: {@code TenantClaimTokenCustomizer}
 * via {@code extractTenantAttribute(principal, key)}. Before these constants the
 * three classes each inlined the same string literals, so a rename in a producer
 * silently broke the consumer at token-issuance time with no compile error. Sharing
 * the key here makes producer/consumer drift a compile-time concern.
 *
 * <p>Pure {@code String} constants — no framework dependency, so this stays a domain
 * POJO. Note these are the {@code details}-map <b>keys</b>, distinct from the JWT
 * output <b>claim</b> names (also {@code "tenant_id"} / {@code "tenant_type"}); the
 * two contracts are kept separate even though the spellings coincide today.
 */
public final class PrincipalDetailKeys {

    /** Tenant id the principal is authenticated under. */
    public static final String TENANT_ID = "tenant_id";

    /** Tenant type (e.g. B2C / enterprise) of {@link #TENANT_ID}. */
    public static final String TENANT_TYPE = "tenant_type";

    /** Account id (subject) of the authenticated principal. */
    public static final String ACCOUNT_ID = "account_id";

    /**
     * Email address of the authenticated principal (TASK-BE-577).
     *
     * <p>Both producers already build the principal as
     * {@code UsernamePasswordAuthenticationToken(email, ...)}, so this value equals
     * {@code Authentication.getName()} today. It is carried here anyway, for the
     * reason this whole class exists: the principal-name spelling is a Spring Security
     * convention a producer may change with no compile error, and a consumer that read
     * it would then mint <em>some other identifier</em> into an {@code email} claim —
     * silently, and into a PII field. The details key makes that drift a compile-time
     * concern instead.
     */
    public static final String EMAIL = "email";

    /**
     * TASK-MONO-771 (ADR-MONO-080 D4) — how the session authenticated: an RFC 8176 {@code amr} value list
     * ({@link AuthenticationMethods}). Written by the two login producers (form {@code ["pwd"]}, social
     * {@code []}) and widened by the second step ({@code /mfa/challenge}, {@code /mfa/setup}); read by
     * {@code TenantClaimTokenCustomizer} (the {@code amr} claim) and by the authorize second-factor gate.
     *
     * <p>🔴 The value MUST be a mutable {@code java.util.ArrayList} — the same SAS
     * {@code SecurityJackson2Modules} allowlist trap as the details map itself ({@code List.of} is not
     * allowlisted and breaks the {@code /oauth2/token} read-back of the stored authorization).
     */
    public static final String AMR = "amr";

    private PrincipalDetailKeys() {
    }
}
