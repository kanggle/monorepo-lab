package com.example.security.servlet;

import com.example.security.oauth2.TenantClaimValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Service-level fail-closed re-enforcement of {@code tenant_id} — the inner layer of the
 * defence in depth whose outer layer is the gateway's {@link TenantClaimValidator}.
 *
 * <p>The validator already runs at JWT-decode time. This filter re-checks <em>after</em>
 * Spring Security has populated the {@code SecurityContext}, so that a token carrying an
 * inadmissible {@code tenant_id} is rejected with <strong>403 {@code TENANT_FORBIDDEN}</strong>
 * even if the {@code JwtDecoder} were misconfigured. It is the layer that is supposed to
 * still be standing when the other one is wrong.
 *
 * <h2>Why this class is parameterised, and what the parameters were before</h2>
 *
 * It replaces <strong>thirteen hand-maintained copies</strong> across erp, fan, finance and
 * scm (ADR-MONO-049 § D5-2). {@code TASK-MONO-382} compared all thirteen and found
 * <strong>eight distinct bodies</strong>, which reduce to exactly <strong>three policy axes</strong>
 * — everything else was line-wrapping, an inlined {@code @Value} import, and locally
 * re-declared claim constants:
 *
 * <table>
 *   <caption>The three axes, as measured</caption>
 *   <tr><th>axis</th><th>what the fleet actually did</th></tr>
 *   <tr>
 *     <td>{@linkplain Builder#exempt(Predicate) public-path exemption}</td>
 *     <td><strong>Three different answers, and this is a security boundary.</strong> Ten
 *         services exempt their project's {@code PublicPaths.isPublic}. {@code fan/membership}
 *         <em>also</em> exempts everything under {@code /internal/}. {@code scm/demand-planning}
 *         and {@code scm/inventory-visibility} ignore {@code PublicPaths} entirely and exempt
 *         <em>all</em> of {@code /actuator/} — wider than their sibling {@code scm/procurement},
 *         which exempts only {@code health}, {@code info} and {@code prometheus}.</td>
 *   </tr>
 *   <tr>
 *     <td>{@linkplain Builder#trustEntitledDomains() entitled-domains}</td>
 *     <td>erp (4), finance (2) and scm (3) honour the signed {@code entitled_domains} claim.
 *         fan (4) does not — fan sits outside the entitlement plane, so the branch would be
 *         dead code there.</td>
 *   </tr>
 *   <tr>
 *     <td>{@linkplain Builder#allowSuperAdminWildcard() wildcard}</td>
 *     <td>All thirteen accept {@code tenant_id="*"}. It is still a switch, and it still
 *         defaults <em>off</em> — see below.</td>
 *   </tr>
 * </table>
 *
 * <h2>Every switch defaults closed</h2>
 *
 * {@link #forTenant(String)} alone enforces exact {@code tenant_id} equality, exempts
 * <strong>nothing</strong>, and rejects the wildcard. Every relaxation is an explicit call at
 * the wiring site. This is {@code TASK-MONO-355}'s rule, carried forward verbatim, and the
 * reason for it is arithmetic: <em>one file now decides the tenant gate for thirteen services,
 * and a shared security class whose defaults open the gate is one typo away from opening all
 * of them.</em>
 *
 * <p>Note in particular that the wildcard defaults <strong>off</strong> even though all
 * thirteen copies allow it today. A default is not a majority vote — it is what you get when
 * someone forgets, and what you want then is the closed gate.
 *
 * <h2>The exemption is a {@link Predicate}, not a class reference</h2>
 *
 * Each project has its own {@code PublicPaths}. A shared library that named one would be
 * reaching into a service module — banned by {@code platform/shared-library-policy.md}
 * § Dependency Rule, and the wrong direction of dependency besides. The consumer passes its
 * own predicate:
 *
 * <pre>{@code
 * TenantClaimEnforcer.forTenant("scm")
 *         .exempt(PublicPaths::isPublic)      // the project's own rule
 *         .allowSuperAdminWildcard()
 *         .trustEntitledDomains()
 *         .build();
 * }</pre>
 */
public class TenantClaimEnforcer extends OncePerRequestFilter implements Ordered {

    /**
     * Runs late, but before the dispatcher. Every one of the thirteen copies used exactly
     * this value; it is the one thing they all agreed on.
     */
    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /** 401 body code when the token carries no usable {@code tenant_id} at all. */
    public static final String CODE_UNAUTHORIZED = "UNAUTHORIZED";

    /**
     * 403 body code when the token's tenant is well-formed but inadmissible here.
     *
     * <p>403, not 401: a cross-tenant token is signature-valid. Telling the client to
     * re-authenticate would be a lie it can loop on forever.
     */
    public static final String CODE_TENANT_FORBIDDEN = "TENANT_FORBIDDEN";

    private static final Logger log = LoggerFactory.getLogger(TenantClaimEnforcer.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String expectedTenantId;
    private final Predicate<HttpServletRequest> exempt;
    private final boolean allowWildcard;
    private final boolean trustEntitledDomains;

    private TenantClaimEnforcer(Builder builder) {
        this.expectedTenantId = builder.expectedTenantId;
        this.exempt = builder.exempt;
        this.allowWildcard = builder.allowWildcard;
        this.trustEntitledDomains = builder.trustEntitledDomains;
    }

    /** Strictest gate: exact {@code tenant_id} equality, nothing exempt. Relax explicitly. */
    public static Builder forTenant(String expectedTenantId) {
        return new Builder(expectedTenantId);
    }

    public static final class Builder {

        /** Exempts nothing. The closed default — see the class Javadoc. */
        private static final Predicate<HttpServletRequest> EXEMPT_NOTHING = request -> false;

        private final String expectedTenantId;
        private Predicate<HttpServletRequest> exempt = EXEMPT_NOTHING;
        private boolean allowWildcard;
        private boolean trustEntitledDomains;

        private Builder(String expectedTenantId) {
            this.expectedTenantId = Objects.requireNonNull(expectedTenantId, "expectedTenantId");
            if (expectedTenantId.isBlank()) {
                throw new IllegalArgumentException("expectedTenantId must not be blank");
            }
        }

        /**
         * Skip tenant enforcement for requests this predicate accepts — typically the
         * project's own {@code PublicPaths::isPublic}.
         *
         * <p><strong>This is the axis the fleet disagreed on, and it is a security boundary.</strong>
         * Whatever this predicate admits is a path on which a cross-tenant token is <em>not</em>
         * checked here. Pass the narrowest rule that is actually true of the service; do not
         * pass a prefix because it is convenient. {@code scm/demand-planning} exempted all of
         * {@code /actuator/} while its sibling {@code scm/procurement} exempted only
         * {@code health}/{@code info}/{@code prometheus} — the difference was invisible because
         * each service kept its own copy of this filter.
         */
        public Builder exempt(Predicate<HttpServletRequest> exempt) {
            this.exempt = Objects.requireNonNull(exempt, "exempt");
            return this;
        }

        /**
         * Accept {@code tenant_id="*"} — the SUPER_ADMIN platform scope, so a platform
         * operator can reach this service during incident response (ADR-MONO-019 § D5).
         *
         * <p>Off by default, even though all thirteen copies turned it on. A default is what
         * you get when someone forgets.
         */
        public Builder allowSuperAdminWildcard() {
            this.allowWildcard = true;
            return this;
        }

        /**
         * Accept a token whose signed {@code entitled_domains} claim contains this service's
         * tenant, even when {@code tenant_id} names another (ADR-MONO-019 § D5 dual-accept).
         *
         * <p>The claim is read only from an RS256/JWKS-verified token, so it is unforgeable,
         * and {@link TenantClaimValidator#isEntitled} fails closed on any claim-shape anomaly.
         * fan does not enable this — it sits outside the entitlement plane.
         */
        public Builder trustEntitledDomains() {
            this.trustEntitledDomains = true;
            return this;
        }

        public TenantClaimEnforcer build() {
            return new TenantClaimEnforcer(this);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return exempt.test(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Not a JWT request: this filter has nothing to say. Authorization is Spring
        // Security's job; every one of the thirteen copies behaved this way.
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }

        Jwt token = jwtAuth.getToken();
        String tenantId = token.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
        boolean entitled = trustEntitledDomains
                && TenantClaimValidator.isEntitled(token, expectedTenantId);

        // A token with neither a tenant_id nor an entitlement carries no tenant context at
        // all, which would leave the persistence layer's WHERE tenant_id filter with nothing
        // to filter on. 401 — but only when the entitlement branch is also silent.
        //
        // The `&& !entitled` is not decoration (TASK-MONO-383). TenantClaimValidator, which
        // runs at decode time in these same services, consults the entitlement relaxation
        // BEFORE it rejects an absent claim. An enforcer that rejected unconditionally would
        // therefore refuse a token the decoder had just admitted — the decode-pass /
        // filter-block split that the thirteen hand-written copies each took care to avoid.
        // When trustEntitledDomains is off (fan), `entitled` is always false and this
        // degenerates to the unconditional rejection those four copies wrote by hand.
        if ((tenantId == null || tenantId.isBlank()) && !entitled) {
            writeError(response, HttpStatus.UNAUTHORIZED.value(),
                    CODE_UNAUTHORIZED, "tenant_id claim is required");
            return;
        }

        // Dual-accept: reject only when BOTH the legacy slug and the signed entitled_domains
        // claim fail. Entitlement only ever widens (fail-closed on any claim-shape anomaly —
        // see TenantClaimValidator#isEntitled).
        if (admitsLegacy(tenantId) || entitled) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("TenantClaimEnforcer rejected cross-tenant request: tenant={} path={}",
                tenantId, request.getRequestURI());
        writeError(response, HttpStatus.FORBIDDEN.value(), CODE_TENANT_FORBIDDEN,
                "tenant_id '" + tenantId + "' is not allowed");
    }

    /** The {@code tenant_id}-only half of the gate: exact match, plus the wildcard if enabled. */
    private boolean admitsLegacy(String tenantId) {
        return expectedTenantId.equals(tenantId)
                || (allowWildcard && TenantClaimValidator.WILDCARD_TENANT.equals(tenantId));
    }

    private static void writeError(HttpServletResponse response, int status,
                                   String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ObjectNode node = JSON.createObjectNode();
        node.put("code", code);
        node.put("message", message);
        node.put("timestamp", Instant.now().toString());
        try {
            response.getWriter().write(JSON.writeValueAsString(node));
        } catch (JsonProcessingException ex) {
            // The envelope is three flat strings; this cannot realistically fail. If it
            // somehow does, still emit a parseable body rather than an empty 403.
            response.getWriter().write(
                    "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        }
    }
}
