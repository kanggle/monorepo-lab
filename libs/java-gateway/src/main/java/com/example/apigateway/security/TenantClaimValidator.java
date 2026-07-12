package com.example.apigateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects access tokens whose {@code tenant_id} claim does not admit this gateway's tenant.
 *
 * <p>Every gateway edge enforces tenant isolation here, at decode time, so a cross-tenant
 * token never reaches an internal service. The validator raises a granular error code so
 * the shared {@code SecurityConfig} can map cross-tenant misuse to <strong>403</strong>
 * rather than 401 — telling a client with a perfectly valid token to "re-authenticate"
 * would be a lie it can never act on.
 *
 * <h2>The gate is parameterized, and the parameters are policy</h2>
 *
 * The four gateways deliberately run <em>different</em> tenant gates (ADR-MONO-048 § D5 —
 * each one traced to a documented decision, not to drift):
 *
 * <table>
 *   <caption>Per-domain gate policy</caption>
 *   <tr><th>domain</th><th>wildcard</th><th>entitlement</th><th>authority</th></tr>
 *   <tr><td>wms</td><td>no</td><td>yes</td><td>strict legacy equality is an explicit choice; ADR-MONO-019 § D5</td></tr>
 *   <tr><td>scm</td><td>yes</td><td>yes</td><td>ADR-MONO-019 § D5 (SUPER_ADMIN incident response)</td></tr>
 *   <tr><td>fan</td><td>yes</td><td>no</td><td>fan sits outside the entitlement plane — the branch would be dead code</td></tr>
 * </table>
 *
 * <p><strong>Both switches default to closed.</strong> The plain
 * {@link #forTenant(String)} gate accepts nothing but an exact {@code tenant_id} match;
 * every relaxation requires an explicit call at the wiring site. A shared security class
 * whose defaults open the gate is one typo away from opening four of them.
 *
 * <p>ecommerce (D7 step 3) adds a fourth: {@link Builder#acceptAnyWellFormedTenant()} —
 * <strong>the only switch in this library that opens a gate rather than narrowing one</strong>.
 * It is justified (ADR-MONO-030 § 2.4: ecommerce is a multi-tenant marketplace SaaS,
 * entitlement is decided at IAM issuance and the edge trusts issuance; tenant separation is
 * enforced by the persistence layer's {@code WHERE tenant_id} and proven by the M6
 * cross-tenant-leak IT), and it is guarded: {@code TenantGatePolicyLeakTest} asserts it is
 * off in wms, scm and fan. A flag that opens an edge needs a test that says where it isn't.
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    /**
     * Sourced from the shared registry so the code this validator <em>raises</em> and the
     * code the shared {@code SecurityConfig} maps to 403 cannot drift apart (TASK-MONO-351).
     */
    public static final String ERROR_CODE_TENANT_MISMATCH = GatewayErrorCodes.TENANT_MISMATCH;
    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_ENTITLED_DOMAINS = "entitled_domains";
    /** SUPER_ADMIN platform-scope wildcard. */
    public static final String WILDCARD_TENANT = "*";

    private final String expectedTenantId;
    private final boolean allowWildcard;
    private final boolean trustEntitledDomains;
    private final boolean acceptAnyWellFormedTenant;

    private TenantClaimValidator(Builder builder) {
        this.expectedTenantId = builder.expectedTenantId;
        this.allowWildcard = builder.allowWildcard;
        this.trustEntitledDomains = builder.trustEntitledDomains;
        this.acceptAnyWellFormedTenant = builder.acceptAnyWellFormedTenant;
    }

    /** Whether this gate admits any well-formed {@code tenant_id}. Exposed so a leak guard can assert it. */
    public boolean acceptsAnyWellFormedTenant() {
        return acceptAnyWellFormedTenant;
    }

    /** Strictest gate: exact {@code tenant_id} equality, nothing else. Relax explicitly. */
    public static Builder forTenant(String expectedTenantId) {
        return new Builder(expectedTenantId);
    }

    public static final class Builder {
        private final String expectedTenantId;
        private boolean allowWildcard;
        private boolean trustEntitledDomains;
        private boolean acceptAnyWellFormedTenant;

        private Builder(String expectedTenantId) {
            this.expectedTenantId = Objects.requireNonNull(expectedTenantId, "expectedTenantId");
        }

        /**
         * Admit <strong>any</strong> non-blank {@code tenant_id} from a verified token. Only
         * a missing / blank / non-string claim is rejected.
         *
         * <p><strong>This is the one switch here that opens a gate.</strong> It exists for the
         * multi-tenant marketplace edge (ADR-MONO-030 § 2.4): ecommerce serves every tenant,
         * entitlement is decided at IAM issuance time, and the edge trusts issuance. Tenant
         * separation is not an edge concern there — it is enforced at the persistence layer by
         * {@code WHERE tenant_id}, which the M6 cross-tenant-leak IT exists to prove.
         *
         * <p>It is <em>not</em> appropriate for a single-domain edge, where the whole point of
         * the gate is that this gateway serves one tenant. Turning it on for wms, scm or fan
         * would silently admit every tenant's tokens — so {@code TenantGatePolicyLeakTest}
         * asserts it is off in all three. Do not remove that guard.
         */
        public Builder acceptAnyWellFormedTenant() {
            this.acceptAnyWellFormedTenant = true;
            return this;
        }

        /**
         * Accept {@code tenant_id="*"} — the SUPER_ADMIN platform scope, so a platform
         * operator can reach this edge during incident response.
         */
        public Builder allowSuperAdminWildcard() {
            this.allowWildcard = true;
            return this;
        }

        /**
         * Accept a token whose GAP-signed {@code entitled_domains} claim contains this
         * gateway's tenant, even when {@code tenant_id} names another one
         * (ADR-MONO-019 § D5 dual-accept). The claim is read only from an RS256/JWKS-verified
         * token, so it is unforgeable; while GAP has not populated it the claim is absent and
         * only the legacy path applies (production net-zero).
         */
        public Builder trustEntitledDomains() {
            this.trustEntitledDomains = true;
            return this;
        }

        public TenantClaimValidator build() {
            return new TenantClaimValidator(this);
        }
    }

    /**
     * Returns {@code true} iff the verified {@code entitled_domains} claim is a list of
     * strings containing {@code domain}. Any claim-shape anomaly (absent / non-list / null
     * or non-string element) yields {@code false} — fail-closed, no NPE, no blanket trust.
     */
    public static boolean isEntitled(Jwt jwt, String domain) {
        if (jwt == null || domain == null) {
            return false;
        }
        return safeStringList(jwt).contains(domain);
    }

    private static List<String> safeStringList(Jwt jwt) {
        Object raw = jwt.getClaims().get(CLAIM_ENTITLED_DOMAINS);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        // getClaimAsStringList would throw on a non-string element; iterate defensively so a
        // malformed claim degrades to "not entitled".
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof String s) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object raw = jwt.getClaim(CLAIM_TENANT_ID);
        String tenantId = raw instanceof String s ? s : null;
        boolean wellFormed = tenantId != null && !tenantId.isBlank();

        boolean legacyOk = wellFormed
                && (expectedTenantId.equals(tenantId)
                        || (allowWildcard && WILDCARD_TENANT.equals(tenantId)));
        if (legacyOk) {
            return OAuth2TokenValidatorResult.success();
        }
        // The marketplace edge (ADR-MONO-030 § 2.4). Still rejects a missing / blank /
        // non-string claim: "any tenant" is not "no tenant" — a token with no tenant context
        // would leave the persistence-layer WHERE tenant_id filter with nothing to filter on.
        if (acceptAnyWellFormedTenant && wellFormed) {
            return OAuth2TokenValidatorResult.success();
        }
        if (trustEntitledDomains && isEntitled(jwt, expectedTenantId)) {
            return OAuth2TokenValidatorResult.success();
        }
        if (tenantId == null || tenantId.isBlank()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE_TENANT_MISMATCH,
                    "tenant_id claim is required",
                    null));
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                ERROR_CODE_TENANT_MISMATCH,
                "tenant_id '" + tenantId + "' is not allowed",
                null));
    }
}
