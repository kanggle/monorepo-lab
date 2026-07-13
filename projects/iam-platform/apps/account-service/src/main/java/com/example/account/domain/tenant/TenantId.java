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
     * Default tenant for the B2C fan platform product — and, measured by TASK-BE-506, the
     * <b>only</b> tenant the consumer surface of this service can produce or see.
     *
     * <p>Both signup use cases stamp this constant on every account they create, and the
     * consumer read/mutation paths ({@code ProfileUseCase}, {@code AccountStatusUseCase},
     * {@code VerifyEmailUseCase}, {@code SendVerificationEmailUseCase},
     * {@code UpdateLastLoginUseCase}) look accounts up under it. Consequences:
     * a shopper who registers through the ecommerce web-store OIDC client is born
     * {@code tenant=fan-platform} (and that value flows into {@code credentials.tenant_id}
     * and the token's {@code tenant_id} claim), while an account provisioned into any other
     * tenant is invisible to those read paths — the same residue TASK-BE-357 already had to
     * fix in the admin email search ({@code AccountQueryPortImpl#findByEmail}).
     *
     * <p>The earlier note here said dynamic injection was "pending TASK-BE-229". That was
     * false: BE-229 is done — it taught auth-service to <i>consume</i> the tenant already on
     * the credential row and never introduced a tenant input to account-service. Resolving
     * the tenant from the initiating OIDC client is TASK-BE-507, which must move the create
     * paths, these read paths, and auth-service's cross-tenant login lookup together.
     */
    public static final TenantId FAN_PLATFORM = new TenantId("fan-platform");

    public TenantId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid tenant_id: " + value);
        }
    }

    /**
     * TASK-BE-467 — resolve an inbound {@code X-Tenant-Id} header into a concrete
     * tenant for the admin account-mutation write-path.
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
