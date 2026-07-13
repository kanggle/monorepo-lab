package com.example.scmplatform.inventoryvisibility.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.security.PublicPaths;
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
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * <strong>scm's tenant policy</strong>, pinned — what inventory-visibility's gate admits, what it
 * refuses, and (this service being one of the two § 1.8 named) <strong>why narrowing its
 * exemption changed nothing that anyone could observe</strong>.
 *
 * <p>Both subjects come out of a real {@link ServiceLevelOAuth2Config}. If someone deletes
 * {@code .allowSuperAdminWildcard()} or {@code .trustEntitledDomains()} or {@code .exempt(...)}
 * from the wiring, <strong>this suite goes red</strong> — a test that built its own
 * {@code forTenant("scm")...} chain would be asserting a builder it wrote itself, and would stay
 * green while the service's real gate silently changed. (ADR-MONO-049, TASK-MONO-385 AC-5.)
 */
@DisplayName("scm/inventory-visibility — the tenant gate's policy, both halves")
class ScmTenantGatePolicyTest {

    private static final String ISSUER = "http://iam.local";
    private static final String API_PATH = "/api/inventory-visibility/nodes/node-1";

    private final ServiceLevelOAuth2Config config = wiredConfig();
    private final OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();
    private final TenantClaimEnforcer enforcer = config.tenantClaimEnforcer();

    private static ServiceLevelOAuth2Config wiredConfig() {
        ServiceLevelOAuth2Config config = new ServiceLevelOAuth2Config();
        ReflectionTestUtils.setField(config, "requiredTenantId", "scm");
        ReflectionTestUtils.setField(config, "allowedIssuersCsv", ISSUER);
        return config;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------------
    // AC-3 — the narrowing decision, and the proof that it is unobservable.
    //
    // Before TASK-MONO-385 this service's filter exempted ALL of /actuator/; SecurityConfig
    // permitted three paths and ended anyRequest().denyAll(). The exemption is now the permit
    // list. The paths that lost their exemption are exactly the paths Spring Security already
    // refuses -- and Spring Security runs FIRST, so the filter never saw them anyway.
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("the exemption IS the permit list (ADR-MONO-049 § 1.8)")
    class ExemptionEqualsThePermitList {

        @Test
        @DisplayName("Spring Security's chain runs BEFORE this filter — so a denied path never reaches it")
        void securityChainRunsFirst() {
            assertThat(TenantClaimEnforcer.ORDER)
                    .as("the enforcer is registered at LOWEST_PRECEDENCE-100; Spring Security's "
                            + "filter chain at %d. A request the authorization filter rejects is "
                            + "answered before this filter is ever invoked — which is why the "
                            + "actuator paths that lost their exemption are unobservable",
                            SecurityProperties.DEFAULT_FILTER_ORDER)
                    .isGreaterThan(SecurityProperties.DEFAULT_FILTER_ORDER);
        }

        @Test
        @DisplayName("the exempt set is exactly the three probes SecurityConfig permits")
        void exemptSetIsThePermitSet() {
            assertThat(PublicPaths.EXACT)
                    .containsExactlyInAnyOrder(
                            "/actuator/health", "/actuator/info", "/actuator/prometheus");
            assertThat(PublicPaths.PREFIXES)
                    .as("no /actuator/health/ prefix: SecurityConfig has never permitted "
                            + "/actuator/health/liveness, and adding it here would widen the "
                            + "permit list under cover of a refactor")
                    .isEmpty();
        }

        @Test
        @DisplayName("the paths that LOST the exemption are all paths SecurityConfig denies")
        void pathsThatLostTheExemptionAreDenied() {
            // The old predicate was `uri.startsWith("/actuator/")`. Everything it exempted that
            // PublicPaths does not is, by construction, absent from SecurityConfig's permit list
            // (which is now PublicPaths itself) — hence denyAll, hence unreachable, hence the
            // narrowing cannot be observed.
            List.of("/actuator/env", "/actuator/beans", "/actuator/loggers",
                            "/actuator/heapdump", "/actuator/health/liveness")
                    .forEach(path -> {
                        assertThat(path).startsWith("/actuator/");          // the old exemption
                        assertThat(PublicPaths.isPublic(path))              // the new one
                                .as("%s must not be exempt", path)
                                .isFalse();
                        assertThat(PublicPaths.EXACT.contains(path))        // ⇒ not permitted
                                .as("%s must not be in the permit list either — that is what "
                                        + "makes the narrowing unobservable", path)
                                .isFalse();
                    });
        }

        @Test
        @DisplayName("/internal/** is untouched: no JWT arrives, so the filter passes it through "
                + "regardless of the exemption (ADR-MONO-027 § D7.1)")
        void internalPathIsNotAnExemptionConcern() throws Exception {
            assertThat(PublicPaths.isPublic("/internal/inventory-visibility/snapshot")).isFalse();
            // No authentication in the context — the unattended batch caller holds no token.
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/internal/inventory-visibility/snapshot");
            request.setRequestURI("/internal/inventory-visibility/snapshot");
            MockHttpServletResponse response = new MockHttpServletResponse();
            RecordingChain chain = new RecordingChain();
            enforcer.doFilter(request, response, chain);
            assertThat(chain.called)
                    .as("a non-JWT request is not this filter's business; narrowing the exemption "
                            + "cannot break the replenishment batch")
                    .isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // The decode-time gate.
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("the decoder admits")
    class DecoderAdmits {

        @Test
        @DisplayName("tenant_id=scm")
        void exactTenant() {
            assertThat(validator.validate(jwt("scm", null)).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=* — the SUPER_ADMIN platform scope")
        void wildcard() {
            assertThat(validator.validate(jwt("*", null)).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=globex-corp + entitled_domains=[scm, erp]")
        void entitledCrossTenant() {
            assertThat(validator.validate(jwt("globex-corp", List.of("scm", "erp"))).hasErrors())
                    .isFalse();
        }

        @Test
        @DisplayName("entitled_domains=[scm] with NO tenant_id at all")
        void entitledWithoutTenantId() {
            assertThat(validator.validate(jwt(null, List.of("scm"))).hasErrors()).isFalse();
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
            assertThat(result.getErrors())
                    .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH
                            .equals(e.getErrorCode()));
        }

        @Test
        @DisplayName("a token entitled somewhere ELSE — with the tenant_mismatch code")
        void entitledElsewhere() {
            var result = validator.validate(jwt("acme", List.of("wms")));
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors())
                    .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH
                            .equals(e.getErrorCode()));
        }

        @Test
        @DisplayName("neither tenant_id nor entitled_domains — with the tenant_mismatch code")
        void noTenantNoEntitlement() {
            var result = validator.validate(jwt(null, null));
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors())
                    .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH
                            .equals(e.getErrorCode()));
        }

        @Test
        @DisplayName("a malformed entitled_domains claim fails CLOSED — non-list, empty, non-string")
        void malformedEntitlementFailsClosed() {
            assertThat(validator.validate(jwtRaw("acme", "scm")).hasErrors()).isTrue();
            assertThat(validator.validate(jwt("acme", List.of())).hasErrors()).isTrue();
            assertThat(validator.validate(jwtRaw("acme", List.of(42))).hasErrors()).isTrue();
            assertThat(TenantClaimValidator.isEntitled(null, "scm")).isFalse();
            assertThat(TenantClaimValidator.isEntitled(jwt("acme", null), null)).isFalse();
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
                    .claim(TenantClaimValidator.CLAIM_TENANT_ID, "scm")
                    .build();
            var result = validator.validate(token);

            assertThat(result.hasErrors()).isTrue();

            assertThat(result.getErrors())

                    .as("the shared AllowedIssuersValidator raises invalid_issuer. The two inline "

                            + "implementations this replaced raised the generic invalid_token, which "

                            + "SecurityConfig.extractOAuth2Error deliberately SKIPS -- so the issuer "

                            + "message never surfaced. Status (401) and code (UNAUTHORIZED) are "

                            + "unchanged; only the response message becomes specific, and it now matches "

                            + "erp, fan and finance. TASK-MONO-385 AC-2, ADR-MONO-049 s 1.10.")

                    .anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
        }
    }

    // -----------------------------------------------------------------------
    // The servlet gate.
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("the filter admits")
    class FilterAdmits {

        @Test
        @DisplayName("tenant_id=scm passes through the chain")
        void exactTenantPasses() throws Exception {
            assertThat(filter(jwt("scm", null), API_PATH).called).isTrue();
        }

        @Test
        @DisplayName("wildcard (*) SUPER_ADMIN passes through")
        void wildcardPasses() throws Exception {
            assertThat(filter(jwt("*", null), API_PATH).called).isTrue();
        }

        @Test
        @DisplayName("tenant_id=acme + entitled_domains=[scm] passes through, 200")
        void entitledCrossTenantPasses() throws Exception {
            Outcome outcome = filter(jwt("acme", List.of("scm")), API_PATH);
            assertThat(outcome.called).isTrue();
            assertThat(outcome.status).isEqualTo(200);
        }

        @Test
        @DisplayName("entitled_domains containing scm grants even when tenant_id is absent")
        void entitledWithoutTenantIdPasses() throws Exception {
            Outcome outcome = filter(jwt(null, List.of("scm")), API_PATH);
            assertThat(outcome.called).isTrue();
            assertThat(outcome.status).isEqualTo(200);
        }

        @Test
        @DisplayName("a permitted actuator probe is exempt — even a cross-tenant token reaches it")
        void publicPathExempt() throws Exception {
            assertThat(filter(jwt("wms", null), "/actuator/health").called).isTrue();
        }
    }

    @Nested
    @DisplayName("the filter refuses")
    class FilterRefuses {

        @Test
        @DisplayName("cross-tenant (wms) → 403 TENANT_FORBIDDEN, chain NOT invoked")
        void crossTenantBlocked() throws Exception {
            Outcome outcome = filter(jwt("wms", null), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
            assertThat(outcome.body).contains(TenantClaimEnforcer.CODE_TENANT_FORBIDDEN);
        }

        @Test
        @DisplayName("non-entitled: tenant_id=acme + entitled_domains=[wms] → 403, chain NOT invoked")
        void nonEntitledCrossTenantBlocked() throws Exception {
            Outcome outcome = filter(jwt("acme", List.of("wms")), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
            assertThat(outcome.body).contains(TenantClaimEnforcer.CODE_TENANT_FORBIDDEN);
        }

        @Test
        @DisplayName("no tenant context at all → 401")
        void noTenantContextIs401() throws Exception {
            Outcome outcome = filter(jwt(null, null), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(401);
        }

        @Test
        @DisplayName("an actuator path that is NOT permitted is still gated — the exemption does "
                + "not leak sideways")
        void nonPublicActuatorPathStillGated() throws Exception {
            Outcome outcome = filter(jwt("wms", null), "/actuator/env");
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("the filter admits exactly what the decoder admits — no decode-pass / filter-block split")
    void theTwoLayersAgree() throws Exception {
        List<Jwt> tokens = List.of(
                jwt("scm", null),
                jwt("*", null),
                jwt("acme", List.of("scm")),
                jwt(null, List.of("scm")),   // the token TASK-MONO-383 found split
                jwt("wms", null),
                jwt("acme", List.of("wms")),
                jwt(null, null));

        for (Jwt token : tokens) {
            boolean decoderAdmits = !validator.validate(token).hasErrors();
            boolean filterAdmits = filter(token, API_PATH).called;
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
