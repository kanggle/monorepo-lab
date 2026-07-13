package com.example.account.domain.tenant;

import java.util.regex.Pattern;

/**
 * Value object for tenant identifier.
 *
 * <p>A tenant_id is a lowercase slug that uniquely identifies a tenant across the platform.
 * The format follows the pattern {@code ^[a-z][a-z0-9-]{1,31}$}:
 * - starts with a lowercase letter
 * - followed by 1–31 characters of lowercase letters, digits, or hyphens
 * - total length: 2–32 characters
 *
 * <p>Examples: {@code fan-platform}, {@code wms}, {@code erp}, {@code scm}
 *
 * <p>Once issued, a tenant_id is immutable — it cannot be changed or reassigned
 * (audit trail and external token integrity).
 */
public record TenantId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    /**
     * Default tenant for the B2C fan platform product, and the <b>fallback</b> whenever no
     * tenant reaches us — never again a hard pin.
     *
     * <p>Until TASK-BE-507 this constant WAS the consumer surface: both signup use cases
     * stamped it on every account they created, and the consumer read paths looked accounts up
     * under it, so a shopper registering through the ecommerce web-store OIDC client was born
     * {@code fan-platform} and that value rode on into {@code credentials.tenant_id} and the
     * token's {@code tenant_id} claim (TASK-BE-506 measured the radius: 8 files, 10 sites).
     * The tenant is now resolved from the initiating OIDC client by auth-service and arrives
     * as {@code X-Tenant-Id}; see {@link #fromHeaderOrDefault(String)}.
     *
     * <p>It remains the value for a header-less caller — that is deliberate and is what makes
     * BE-507 net-zero for everything that worked before (every account created before BE-507
     * lives here, and form login still finds them: {@code CredentialAuthenticationProvider}
     * falls back to a cross-tenant lookup when the client-scoped one misses).
     */
    public static final TenantId FAN_PLATFORM = new TenantId("fan-platform");

    public TenantId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid tenant_id: " + value);
        }
    }

    /**
     * TASK-BE-467 — resolve an inbound {@code X-Tenant-Id} header into a concrete tenant for
     * the admin account-mutation write-path. TASK-BE-507 extended it to the <b>consumer</b>
     * surface: both signup paths and the consumer read paths (profile / status / email
     * verification) now resolve their tenant through here instead of pinning the constant.
     *
     * <p><b>NET-ZERO default:</b> an absent, blank, or platform-scope wildcard
     * ({@code "*"}) header pins to {@link #FAN_PLATFORM} — byte-identical to the
     * pre-BE-467 hard-pin, so the only current holder (SUPER_ADMIN {@code '*'}) and
     * any header-less caller keep today's behavior. A concrete tenant slug is
     * honored, so a cross-tenant target resolves through the tenant-scoped
     * {@code findById} to an enumeration-safe {@code 404} (confinement for free —
     * never a 403 that would confirm the account exists in another tenant).
     */
    public static TenantId fromHeaderOrDefault(String headerValue) {
        if (headerValue == null || headerValue.isBlank() || "*".equals(headerValue)) {
            return FAN_PLATFORM;
        }
        return new TenantId(headerValue);
    }

    @Override
    public String toString() {
        return value;
    }
}
