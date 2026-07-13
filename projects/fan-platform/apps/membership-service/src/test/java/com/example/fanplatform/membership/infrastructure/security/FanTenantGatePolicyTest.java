package com.example.fanplatform.membership.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fanplatform.membership.presentation.security.PublicPaths;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.TenantClaimEnforcer;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
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
 * <strong>fan's tenant policy</strong>, pinned — and fan is the project where the interesting
 * half of the policy is what it <em>refuses</em>.
 *
 * <h2>fan is the first project that leaves a switch OFF</h2>
 *
 * Every other domain honours the signed {@code entitled_domains} claim. fan does not — none of
 * its four hand-written copies ever held an {@code isEntitled} branch (measured: zero). So
 * {@link ServiceLevelOAuth2Config} does <strong>not</strong> call
 * {@code .trustEntitledDomains()}, and an entitled cross-tenant token must be
 * <strong>refused</strong> here while the same token is admitted in erp, finance and scm.
 *
 * <p>That refusal is the assertion this suite exists for. {@code TASK-MONO-355} found that wms's
 * rejection of the wildcard — its one distinctive policy — had <em>zero</em> coverage, because
 * the suite only recorded what the gate accepted. <strong>The same trap is set here, in the
 * opposite direction:</strong> a suite that only records fan accepting `fan-platform` stays green
 * if someone adds {@code .trustEntitledDomains()} and quietly widens the gate.
 *
 * <p>Both subjects come out of a real {@link ServiceLevelOAuth2Config} — a test that built its
 * own {@code forTenant(...)} chain would be asserting a builder it wrote itself.
 */
@DisplayName("fan/membership-service — the tenant gate's policy, and what it refuses")
class FanTenantGatePolicyTest {

    private static final String ISSUER = "http://iam.local";
    private static final String TENANT = "fan-platform";
    private static final String API_PATH = "/api/fan/memberships/m-1";

    private final ServiceLevelOAuth2Config config = wiredConfig();
    private final OAuth2TokenValidator<Jwt> validator = config.endUserTokenValidator();
    private final TenantClaimEnforcer enforcer = config.tenantClaimEnforcer();

    private static ServiceLevelOAuth2Config wiredConfig() {
        ServiceLevelOAuth2Config config = new ServiceLevelOAuth2Config();
        ReflectionTestUtils.setField(config, "requiredTenantId", TENANT);
        ReflectionTestUtils.setField(config, "allowedIssuersCsv", ISSUER);
        return config;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------------
    // The switch that stays OFF. This is the half that would rot silently.
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("entitled_domains is NOT trusted — fan sits outside the entitlement plane")
    class EntitlementIsRefused {

        @Test
        @DisplayName("the DECODER refuses a token entitled to fan but carrying another tenant_id")
        void decoderRefusesEntitledCrossTenant() {
            var result = validator.validate(jwt("wms", List.of(TENANT)));
            assertThat(result.hasErrors())
                    .as("erp, finance and scm would admit this token. fan must not — and if this "
                            + "assertion ever goes green, someone has added "
                            + ".trustEntitledDomains() to the wiring")
                    .isTrue();
            assertThat(result.getErrors())
                    .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH
                            .equals(e.getErrorCode()));
        }

        @Test
        @DisplayName("the FILTER refuses the same token — 403, chain NOT invoked")
        void filterRefusesEntitledCrossTenant() throws Exception {
            Outcome outcome = filter(jwt("wms", List.of(TENANT)), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
            assertThat(outcome.body).contains(TenantClaimEnforcer.CODE_TENANT_FORBIDDEN);
        }

        @Test
        @DisplayName("an ABSENT tenant_id is 401 even WITH an entitlement — § 1.9's null branch "
                + "degenerates to the unconditional rejection fan's copies wrote by hand")
        void absentTenantIs401EvenWhenEntitled() throws Exception {
            // The canonical filter's 401 branch reads `(tenantId == null || blank) && !entitled`.
            // With trustEntitledDomains OFF, `entitled` is always false, so it collapses to the
            // unconditional `tenantId == null || blank` that all four fan copies wrote.
            Outcome outcome = filter(jwt(null, List.of(TENANT)), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(401);

            assertThat(validator.validate(jwt(null, List.of(TENANT))).hasErrors())
                    .as("and the decoder agrees — the two layers must not split")
                    .isTrue();
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("the decoder admits")
    class DecoderAdmits {

        @Test
        @DisplayName("tenant_id=fan-platform")
        void exactTenant() {
            assertThat(validator.validate(jwt(TENANT, null)).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=* — the SUPER_ADMIN platform scope (wildcard switch ON)")
        void wildcard() {
            assertThat(validator.validate(jwt("*", null)).hasErrors()).isFalse();
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
        @DisplayName("a blank tenant_id")
        void blankTenant() {
            assertThat(validator.validate(jwt("   ", null)).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("no tenant_id at all")
        void absentTenant() {
            assertThat(validator.validate(jwt(null, null)).hasErrors()).isTrue();
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
                    .claim(TenantClaimValidator.CLAIM_TENANT_ID, TENANT)
                    .build();
            var result = validator.validate(token);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors())
                    .as("fan's copy already raised invalid_issuer; the shared class raises the "
                            + "same code, so no response changes (TASK-MONO-387 AC-2)")
                    .anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("the filter admits")
    class FilterAdmits {

        @Test
        @DisplayName("tenant_id=fan-platform passes through the chain")
        void exactTenantPasses() throws Exception {
            assertThat(filter(jwt(TENANT, null), API_PATH).called).isTrue();
        }

        @Test
        @DisplayName("tenant_id=* passes — the wildcard switch is ON in the wiring")
        void wildcardPasses() throws Exception {
            assertThat(filter(jwt("*", null), API_PATH).called).isTrue();
        }

        @Test
        @DisplayName("a public actuator probe is exempt — even a cross-tenant token reaches it")
        void publicPathExempt() throws Exception {
            assertThat(filter(jwt("wms", null), "/actuator/health").called).isTrue();
            assertThat(filter(jwt("wms", null), "/actuator/health/liveness").called)
                    .as("the /actuator/health/ subtree is in fan's PublicPaths")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("the filter refuses")
    class FilterRefuses {

        @Test
        @DisplayName("a cross-tenant token → 403 TENANT_FORBIDDEN, chain NOT invoked")
        void crossTenantBlocked() throws Exception {
            Outcome outcome = filter(jwt("wms", null), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
            assertThat(outcome.body).contains(TenantClaimEnforcer.CODE_TENANT_FORBIDDEN);
        }

        @Test
        @DisplayName("no tenant_id → 401")
        void absentTenantIs401() throws Exception {
            Outcome outcome = filter(jwt(null, null), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(401);
        }

        @Test
        @DisplayName("a NON-public actuator path is still gated — community's fail-closed "
                + "judgement, now held for the whole project")
        void nonPublicActuatorPathStillGated() throws Exception {
            // community's copy carried this reasoning by hand: "a blanket /actuator/ prefix would
            // bypass the tenant gate for endpoints that may be added later (/actuator/env,
            // /actuator/heapdump, …); we want a fail-closed posture there." scm's two services
            // did exactly what community refused (ADR-MONO-049 § 1.8). fan was right, and being
            // right was invisible. It is not invisible now.
            assertThat(PublicPaths.isPublic("/actuator/env")).isFalse();
            Outcome outcome = filter(jwt("wms", null), "/actuator/env");
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
        }
    }


    // -----------------------------------------------------------------------
    // membership's one deviation: /internal/** is exempt. ADR-MONO-049 § 1.8 left this as a
    // decision. It is load-bearing, and these assertions are why we know that rather than
    // believe it (TASK-MONO-387 AC-6).
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("/internal/** is exempt — and it has to be")
    class InternalPrefixIsExempt {

        @Test
        @DisplayName("a workload-identity token — no tenant_id at all — reaches /internal/**")
        void workloadIdentityTokenReachesInternal() throws Exception {
            // This is exactly the token the @Order(1) internal chain produces: internalJwtDecoder
            // "does NOT pin tenant_id" (its own Javadoc), and the chain still puts a
            // JwtAuthenticationToken in the context. Without the exemption the gate 401s it --
            // and community/HttpMembershipChecker stops working.
            Outcome outcome = filter(jwt(null, null), "/internal/membership/access-check");
            assertThat(outcome.called)
                    .as("remove the /internal/ clause from the exemption and this goes red; that "
                            + "is how we know the clause is not decoration")
                    .isTrue();
        }

        @Test
        @DisplayName("the same tokenless request on an END-USER route is 401 — the exemption is a "
                + "hole in one wall, not in the building")
        void sameTokenOnEndUserRouteIs401() throws Exception {
            Outcome outcome = filter(jwt(null, null), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(401);
        }

        @Test
        @DisplayName("/internal/** is NOT in PublicPaths — it is authenticated, just not by tenant")
        void internalIsNotPublic() {
            // The @Order(1) chain requires hasRole("INTERNAL"). A tenant claim is not the thing
            // standing between a caller and that surface, so exempting it from the TENANT gate
            // does not make it public.
            assertThat(PublicPaths.isPublic("/internal/membership/access-check")).isFalse();
        }

        @Test
        @DisplayName("a cross-tenant END-USER token is still refused — the exemption does not leak")
        void internalExemptionDoesNotLeakSideways() throws Exception {
            Outcome outcome = filter(jwt("wms", null), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
        }
    }
    @Test
    @DisplayName("the filter admits exactly what the decoder admits — no decode-pass / filter-block split")
    void theTwoLayersAgree() throws Exception {
        List<Jwt> tokens = List.of(
                jwt(TENANT, null),
                jwt("*", null),
                jwt("wms", null),
                jwt("wms", List.of(TENANT)),   // entitled — refused on BOTH layers in fan
                jwt(null, List.of(TENANT)),
                jwt(null, null));

        for (Jwt token : tokens) {
            boolean decoderAdmits = !validator.validate(token).hasErrors();
            boolean filterAdmits = filter(token, API_PATH).called;
            assertThat(filterAdmits)
                    .as("decoder and filter disagree on %s", token.getClaims())
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

    private static final class RecordingChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            this.called = true;
        }
    }
}
