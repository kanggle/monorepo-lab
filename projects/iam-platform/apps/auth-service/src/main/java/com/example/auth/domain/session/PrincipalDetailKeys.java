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

    private PrincipalDetailKeys() {
    }
}
