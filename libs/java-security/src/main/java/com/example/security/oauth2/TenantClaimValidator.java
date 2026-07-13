package com.example.security.oauth2;

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
 * The gateways deliberately run <em>different</em> tenant gates (ADR-MONO-048 § D5 —
 * each one traced to a documented decision, not to drift):
 *
 * <table>
 *   <caption>Per-domain gate policy</caption>
 *   <tr><th>domain</th><th>wildcard</th><th>entitlement</th><th>authority</th></tr>
 *   <tr><td>wms</td><td>no</td><td>yes</td><td>strict legacy equality is an explicit choice; ADR-MONO-019 § D5</td></tr>
 *   <tr><td>scm</td><td>yes</td><td>yes</td><td>ADR-MONO-019 § D5 (SUPER_ADMIN incident response)</td></tr>
 *   <tr><td>fan</td><td>yes</td><td>no</td><td>fan sits outside the entitlement plane — the branch would be dead code</td></tr>
 *   <tr><td>erp / finance / ecommerce</td><td>yes</td><td>yes</td><td>ADR-MONO-019 § D5; ecommerce is the marketplace edge (ADR-MONO-030 § D1-A) and reaches it via entitlement, like everyone else</td></tr>
 * </table>
 *
 * <p><strong>Every switch defaults to closed, and every switch narrows.</strong> The plain
 * {@link #forTenant(String)} gate accepts nothing but an exact {@code tenant_id} match;
 * every relaxation requires an explicit call at the wiring site. A shared security class
 * whose defaults open the gate is one typo away from opening all of them.
 *
 * <p><strong>There used to be a switch that opened one.</strong> {@code acceptAnyWellFormedTenant}
 * admitted <em>any</em> non-blank {@code tenant_id}, and ecommerce was its only caller — the
 * marketplace edge serves every tenant, so "does this token's tenant match this gateway's?" was
 * held to be a question it could not ask. But the question it actually needed was
 * <em>"is this tenant entitled to <b>this</b> domain?"</em>, and that is what
 * {@link Builder#trustEntitledDomains()} asks — the same thing erp, finance, scm and wms ask.
 * Accepting any well-formed tenant answered a <em>weaker</em> question and let a token entitled
 * only to some <em>other</em> domain through (TASK-BE-506). ecommerce now runs the fleet's gate
 * and the opening switch is gone (TASK-MONO-388, ADR-MONO-049 § D5). <strong>Do not reintroduce
 * it.</strong> A marketplace edge is not a gate that admits everyone; it is a gate whose
 * admissible set is decided by entitlement rather than by its own name.
 */
public class TenantClaimValidator implements OAuth2TokenValidator<Jwt> {

    /**
     * The wire value a cross-tenant rejection carries, so the code this validator
     * <em>raises</em> and the code a {@code SecurityConfig} maps to 403 cannot drift apart
     * (TASK-MONO-351).
     *
     * <p><strong>This class now owns the literal; it used to borrow it.</strong> While the
     * validator was per-domain, {@code libs/java-gateway}'s {@code GatewayErrorCodes} held the
     * value and this field delegated to it — that was the only way to have one definition
     * without the library reaching into a service class. Now that the validator itself is
     * shared, and lives in a module {@code java-gateway} <em>depends on</em>, the delegation
     * has to run the other way or the two modules form a cycle. So the arrow is reversed:
     * {@code GatewayErrorCodes.TENANT_MISMATCH} points here. **One literal, both sides pointing
     * at it** — MONO-351's property, unchanged; only its direction moved. (ADR-MONO-049 § D5-1.)
     */
    public static final String ERROR_CODE_TENANT_MISMATCH = "tenant_mismatch";
    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_ENTITLED_DOMAINS = "entitled_domains";
    /** SUPER_ADMIN platform-scope wildcard. */
    public static final String WILDCARD_TENANT = "*";

    private final String expectedTenantId;
    private final boolean allowWildcard;
    private final boolean trustEntitledDomains;

    private TenantClaimValidator(Builder builder) {
        this.expectedTenantId = builder.expectedTenantId;
        this.allowWildcard = builder.allowWildcard;
        this.trustEntitledDomains = builder.trustEntitledDomains;
    }

    /** Strictest gate: exact {@code tenant_id} equality, nothing else. Relax explicitly. */
    public static Builder forTenant(String expectedTenantId) {
        return new Builder(expectedTenantId);
    }

    public static final class Builder {
        private final String expectedTenantId;
        private boolean allowWildcard;
        private boolean trustEntitledDomains;

        private Builder(String expectedTenantId) {
            this.expectedTenantId = Objects.requireNonNull(expectedTenantId, "expectedTenantId");
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
