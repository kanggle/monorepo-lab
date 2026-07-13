package com.example.finance.ledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.TenantClaimEnforcer;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * <strong>finance's tenant policy</strong>, pinned — what ledger-service's gate admits and,
 * just as importantly, what it refuses.
 *
 * <p>ADR-MONO-049 § D5-3 replaced this service's three hand-maintained security classes with the
 * shared ones. In the shared classes <em>every switch defaults closed</em>, so finance's actual
 * policy — wildcard on, entitlement trusted, {@code PublicPaths} exempt — is now stated in
 * {@link ServiceLevelOAuth2Config} and nowhere else.
 *
 * <h2>Why this test builds its subjects from the config, and never with its own builder</h2>
 *
 * Both objects under test come out of a real {@link ServiceLevelOAuth2Config}. That is the whole
 * point: if someone deletes {@code .allowSuperAdminWildcard()} or {@code .trustEntitledDomains()}
 * or {@code .exempt(...)} from the wiring, <strong>this suite goes red</strong>. A test that
 * constructed its own {@code TenantClaimEnforcer.forTenant("finance")....build()} would be
 * asserting a builder it wrote itself, and would stay green while the service's real gate
 * silently narrowed to 403-everything. (TASK-MONO-383 AC-5.)
 *
 * <p>The class-level contract of the shared classes — fail-closed on malformed claims, the 401/403
 * split, the filter order — is asserted once, in the library's own suites, and inherited here.
 */
@DisplayName("finance/ledger-service — the tenant gate's policy, both halves")
class FinanceTenantGatePolicyTest {

    private static final String ISSUER = "http://iam.local";

    private final ServiceLevelOAuth2Config config = wiredConfig();

    /** The decode-time chain, exactly as the service builds it. */
    private final OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();

    /** The servlet filter, exactly as the service builds it. */
    private final TenantClaimEnforcer enforcer = config.tenantClaimEnforcer();

    private static ServiceLevelOAuth2Config wiredConfig() {
        ServiceLevelOAuth2Config config = new ServiceLevelOAuth2Config();
        ReflectionTestUtils.setField(config, "requiredTenantId", "finance");
        ReflectionTestUtils.setField(config, "allowedIssuersCsv", ISSUER);
        return config;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------------
    // The decode-time gate (TenantClaimValidator, as finance wires it).
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("the decoder admits")
    class DecoderAdmits {

        @Test
        @DisplayName("tenant_id=finance")
        void exactTenant() {
            assertThat(validator.validate(jwt("finance", null)).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=* — the SUPER_ADMIN platform scope (wildcard switch ON)")
        void wildcard() {
            assertThat(validator.validate(jwt("*", null)).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=acme + entitled_domains=[finance] (entitlement switch ON)")
        void entitledCrossTenant() {
            assertThat(validator.validate(jwt("acme", List.of("finance"))).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("entitled_domains=[finance] with NO tenant_id at all")
        void entitledWithoutTenantId() {
            assertThat(validator.validate(jwt(null, List.of("finance"))).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("the legacy slugs still pass when an entitlement claim is present but useless")
        void legacyStillPasses() {
            assertThat(validator.validate(jwt("finance", List.of("wms"))).hasErrors()).isFalse();
            assertThat(validator.validate(jwt("*", List.of("wms"))).hasErrors()).isFalse();
        }
    }

    @Nested
    @DisplayName("the decoder refuses")
    class DecoderRefuses {

        @Test
        @DisplayName("a cross-tenant token — with the tenant_mismatch code")
        void crossTenant() {
            var result = validator.validate(jwt("wms", null));
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors().iterator().next().getErrorCode())
                    .isEqualTo(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("a token entitled somewhere ELSE — with the tenant_mismatch code")
        void entitledElsewhere() {
            var result = validator.validate(jwt("acme", List.of("wms")));
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors().iterator().next().getErrorCode())
                    .isEqualTo(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("a cross-tenant token with no entitlement claim at all")
        void crossTenantNoEntitlement() {
            assertThat(validator.validate(jwt("acme", null)).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("a token with no tenant context whatsoever")
        void noTenantContext() {
            assertThat(validator.validate(jwt(null, null)).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("a malformed entitled_domains claim — non-list, empty, non-string element")
        void malformedEntitlementFailsClosed() {
            assertThat(validator.validate(jwtRaw("acme", "finance")).hasErrors())
                    .as("a bare String, not a list")
                    .isTrue();
            assertThat(validator.validate(jwt("acme", List.of())).hasErrors()).isTrue();
            assertThat(validator.validate(jwtRaw("acme", List.of(42))).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("an issuer outside the allowlist — the other leg of the same chain")
        void unlistedIssuer() {
            Jwt token = Jwt.withTokenValue("t")
                    .header("alg", "RS256")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .claim("iss", "http://evil")
                    .claim("sub", "s")
                    .claim(TenantClaimValidator.CLAIM_TENANT_ID, "finance")
                    .build();
            assertThat(validator.validate(token).hasErrors()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // The servlet gate (TenantClaimEnforcer, as finance wires it). It must reach the
    // SAME verdict as the decoder above — see ADR-MONO-049 § 1.8 / TASK-MONO-383.
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("the filter admits")
    class FilterAdmits {

        @Test
        @DisplayName("tenant_id=finance passes through the chain")
        void exactTenantPasses() throws Exception {
            assertThat(filter(jwt("finance", null), "/api/finance/ledger/entries/e-1").called).isTrue();
        }

        @Test
        @DisplayName("tenant_id=* passes — the wildcard switch is ON in the wiring")
        void wildcardPasses() throws Exception {
            assertThat(filter(jwt("*", null), "/api/finance/ledger/entries/e-1").called).isTrue();
        }

        @Test
        @DisplayName("tenant_id=acme + entitled_domains=[finance] passes, 200")
        void entitledCrossTenantPasses() throws Exception {
            Outcome outcome = filter(jwt("acme", List.of("finance")), "/api/finance/ledger/entries/e-1");
            assertThat(outcome.called).isTrue();
            assertThat(outcome.status).isEqualTo(200);
        }

        @Test
        @DisplayName("a public actuator path is exempt — even a cross-tenant token reaches it")
        void publicPathExempt() throws Exception {
            // The old suite asserted this by calling the protected shouldNotFilter() hook, which
            // a same-package copy allowed. The shared filter is not in this package, so it is
            // asserted where it actually matters instead: through doFilter, with a token the gate
            // would otherwise refuse. Same property, observed rather than introspected.
            assertThat(filter(jwt("wms", null), "/actuator/health").called).isTrue();
        }
    }

    @Nested
    @DisplayName("the filter refuses")
    class FilterRefuses {

        @Test
        @DisplayName("a cross-tenant token → 403 TENANT_FORBIDDEN, chain NOT invoked")
        void crossTenantBlocked() throws Exception {
            Outcome outcome = filter(jwt("wms", null), "/api/finance/ledger/entries/e-1");
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
            assertThat(outcome.body).contains(TenantClaimEnforcer.CODE_TENANT_FORBIDDEN);
        }

        @Test
        @DisplayName("entitled somewhere else → 403 TENANT_FORBIDDEN, chain NOT invoked")
        void nonEntitledCrossTenantBlocked() throws Exception {
            Outcome outcome = filter(jwt("acme", List.of("wms")), "/api/finance/ledger/entries/e-1");
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
            assertThat(outcome.body).contains(TenantClaimEnforcer.CODE_TENANT_FORBIDDEN);
        }

        @Test
        @DisplayName("no tenant context at all → 401, not 403 — there is nothing to be forbidden from")
        void noTenantContextIs401() throws Exception {
            Outcome outcome = filter(jwt(null, null), "/api/finance/ledger/entries/e-1");
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(401);
        }

        @Test
        @DisplayName("a NON-public path is still gated — the exemption does not leak sideways")
        void nonPublicPathStillGated() throws Exception {
            // /actuator/env is NOT in finance's PublicPaths. If the exempt() predicate were ever
            // widened to a bare "/actuator/" prefix — the shape scm/demand-planning wrote by hand
            // (ADR-MONO-049 § 1.8) — this assertion is what would notice.
            Outcome outcome = filter(jwt("wms", null), "/actuator/env");
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
        }
    }

    // -----------------------------------------------------------------------
    // The two layers must not disagree.
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("the filter admits exactly what the decoder admits — no decode-pass / filter-block split")
    void theTwoLayersAgree() throws Exception {
        List<Jwt> tokens = List.of(
                jwt("finance", null),
                jwt("*", null),
                jwt("acme", List.of("finance")),
                jwt(null, List.of("finance")),   // the token TASK-MONO-383 found split
                jwt("wms", null),
                jwt("acme", List.of("wms")),
                jwt(null, null));

        for (Jwt token : tokens) {
            boolean decoderAdmits = !validator.validate(token).hasErrors();
            boolean filterAdmits = filter(token, "/api/finance/ledger/entries/e-1").called;
            assertThat(filterAdmits)
                    .as("decoder and filter disagree on %s — the inner layer of a defence in "
                            + "depth must not refuse what the outer one just admitted",
                            token.getClaims())
                    .isEqualTo(decoderAdmits);
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private Outcome filter(Jwt token, String uri) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();
        enforcer.doFilter(request, response, chain);
        SecurityContextHolder.clearContext();
        return new Outcome(chain.called, response.getStatus(), response.getContentAsString());
    }

    private record Outcome(boolean called, int status, String body) {}

    private static Jwt jwt(String tenantId, List<String> entitledDomains) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("iss", ISSUER)
                .claim("sub", "s");
        if (tenantId != null) {
            builder.claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        }
        if (entitledDomains != null) {
            builder.claim(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, entitledDomains);
        }
        return builder.build();
    }

    /** {@code entitled_domains} carrying a shape the claim is not supposed to have. */
    private static Jwt jwtRaw(String tenantId, Object malformedEntitled) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(Map.of(
                        "iss", ISSUER,
                        "sub", "s",
                        TenantClaimValidator.CLAIM_TENANT_ID, tenantId,
                        TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, malformedEntitled)))
                .build();
    }

    private static final class RecordingChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            this.called = true;
        }
    }
}
