package com.example.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.oauth2.TenantClaimValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The canonical enforcer's contract — and, for every switch, <strong>what it refuses</strong>.
 *
 * <p>That second half is the point. {@code TASK-MONO-355} found that wms's rejection of the
 * {@code "*"} wildcard — the most distinctive gate in the fleet — had <em>zero</em> test
 * coverage, because its suite only recorded what the gate accepted. A suite written that way
 * stays green when a switch quietly disappears. So each switch here is asserted twice: once
 * on, once <strong>off</strong>.
 *
 * <p>This matters more here than it did there. One file now decides the tenant gate for
 * thirteen services (ADR-MONO-049 § D5-2).
 */
@DisplayName("TenantClaimEnforcer — the canonical servlet tenant gate")
class TenantClaimEnforcerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final RecordingChain chain = new RecordingChain();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------------
    // Defaults. Every switch closed — TASK-MONO-355's rule, carried forward.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("defaults are closed")
    class DefaultsAreClosed {

        private final TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("scm").build();

        @Test
        @DisplayName("the exact tenant passes")
        void exactTenantPasses() throws Exception {
            authenticate(jwt("scm", null));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertThat(chain.called).isTrue();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("the wildcard is REFUSED by default — even though all thirteen copies allowed it")
        void wildcardRefusedByDefault() throws Exception {
            authenticate(jwt(TenantClaimValidator.WILDCARD_TENANT, null));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertForbidden();
        }

        @Test
        @DisplayName("entitled_domains is IGNORED by default")
        void entitledIgnoredByDefault() throws Exception {
            authenticate(jwt("erp", List.of("scm")));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertForbidden();
        }

        @Test
        @DisplayName("NOTHING is exempt by default — not even /actuator/health")
        void nothingExemptByDefault() throws Exception {
            authenticate(jwt("erp", null));
            enforcer.doFilter(get("/actuator/health"), response, chain);
            assertForbidden();
        }

        @Test
        @DisplayName("a blank tenant is rejected before any relaxation is consulted")
        void blankTenantRejected() {
            assertThatThrownBy(() -> TenantClaimEnforcer.forTenant(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---------------------------------------------------------------------
    // The exemption axis — the one the fleet disagreed on, and a security boundary.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("exempt(Predicate) — the axis the thirteen copies disagreed on")
    class ExemptionAxis {

        @Test
        @DisplayName("an exempt path skips the gate entirely — a cross-tenant token passes")
        void exemptPathSkipsTheGate() throws Exception {
            TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("scm")
                    .exempt(r -> r.getRequestURI().startsWith("/actuator/"))
                    .build();
            authenticate(jwt("erp", null));   // WRONG tenant
            enforcer.doFilter(get("/actuator/env"), response, chain);
            assertThat(chain.called)
                    .as("an exemption is a hole in the gate — that is what it is FOR, and why "
                            + "the predicate should be the narrowest rule that is actually true")
                    .isTrue();
        }

        @Test
        @DisplayName("a NON-exempt path is still gated — the exemption does not leak sideways")
        void nonExemptPathStillGated() throws Exception {
            TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("scm")
                    .exempt(r -> r.getRequestURI().startsWith("/actuator/"))
                    .build();
            authenticate(jwt("erp", null));
            enforcer.doFilter(get("/api/scm/orders"), response, chain);
            assertForbidden();
        }

        @Test
        @DisplayName("the fan/membership shape: /internal/** exempt, everything else gated")
        void membershipShape() throws Exception {
            TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("fan-platform")
                    .exempt(r -> r.getRequestURI().startsWith("/internal/"))
                    .allowSuperAdminWildcard()
                    .build();

            authenticate(jwt("scm", null));
            enforcer.doFilter(get("/internal/membership/check"), response, chain);
            assertThat(chain.called).isTrue();

            MockHttpServletResponse second = new MockHttpServletResponse();
            RecordingChain secondChain = new RecordingChain();
            enforcer.doFilter(get("/api/fan/memberships"), second, secondChain);
            assertThat(secondChain.called).isFalse();
            assertThat(second.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    // ---------------------------------------------------------------------
    // The wildcard axis — asserted BOTH ways.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("allowSuperAdminWildcard()")
    class WildcardAxis {

        @Test
        @DisplayName("ON: tenant_id=\"*\" passes")
        void wildcardAdmittedWhenOn() throws Exception {
            TenantClaimEnforcer enforcer =
                    TenantClaimEnforcer.forTenant("scm").allowSuperAdminWildcard().build();
            authenticate(jwt(TenantClaimValidator.WILDCARD_TENANT, null));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertThat(chain.called).isTrue();
        }

        @Test
        @DisplayName("OFF: tenant_id=\"*\" is REFUSED — this is the assertion MONO-355 found missing")
        void wildcardRefusedWhenOff() throws Exception {
            TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("scm").build();
            authenticate(jwt(TenantClaimValidator.WILDCARD_TENANT, null));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertForbidden();
        }

        @Test
        @DisplayName("ON does not admit some OTHER tenant — the switch opens one door, not the wall")
        void wildcardOnStillRejectsOtherTenants() throws Exception {
            TenantClaimEnforcer enforcer =
                    TenantClaimEnforcer.forTenant("scm").allowSuperAdminWildcard().build();
            authenticate(jwt("erp", null));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertForbidden();
        }
    }

    // ---------------------------------------------------------------------
    // The entitled-domains axis — asserted BOTH ways, and fail-closed on malformed input.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("trustEntitledDomains()")
    class EntitledDomainsAxis {

        @Test
        @DisplayName("ON: a token entitled to this domain passes even though tenant_id names another")
        void entitledAdmittedWhenOn() throws Exception {
            TenantClaimEnforcer enforcer =
                    TenantClaimEnforcer.forTenant("scm").trustEntitledDomains().build();
            authenticate(jwt("erp", List.of("erp", "scm")));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertThat(chain.called).isTrue();
        }

        @Test
        @DisplayName("OFF: the same token is REFUSED — fan's shape, and the branch is not dead code")
        void entitledRefusedWhenOff() throws Exception {
            TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("scm").build();
            authenticate(jwt("erp", List.of("erp", "scm")));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertForbidden();
        }

        @Test
        @DisplayName("ON: entitlement to a DIFFERENT domain does not admit")
        void entitledToAnotherDomainRefused() throws Exception {
            TenantClaimEnforcer enforcer =
                    TenantClaimEnforcer.forTenant("scm").trustEntitledDomains().build();
            authenticate(jwt("erp", List.of("erp", "finance")));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertForbidden();
        }

        @Test
        @DisplayName("ON: a malformed entitled_domains claim fails CLOSED, it does not throw")
        void malformedEntitledFailsClosed() throws Exception {
            TenantClaimEnforcer enforcer =
                    TenantClaimEnforcer.forTenant("scm").trustEntitledDomains().build();
            authenticate(jwtWithRawClaim("erp", "scm"));   // a String, not a List
            enforcer.doFilter(get("/api/x"), response, chain);
            assertForbidden();
        }

        @Test
        @DisplayName("ON: an ABSENT tenant_id but a valid entitlement passes — the switch reaches "
                + "the 401 branch too, not just the 403 one")
        void entitledWithoutTenantIdAdmittedWhenOn() throws Exception {
            TenantClaimEnforcer enforcer =
                    TenantClaimEnforcer.forTenant("scm").trustEntitledDomains().build();
            authenticate(jwt(null, List.of("scm")));
            enforcer.doFilter(get("/api/x"), response, chain);
            assertThat(chain.called)
                    .as("TenantClaimValidator consults the entitlement relaxation BEFORE it "
                            + "rejects an absent claim. An enforcer that rejected here would "
                            + "refuse a token the decoder had just admitted (TASK-MONO-383)")
                    .isTrue();
        }

        @Test
        @DisplayName("OFF: an absent tenant_id is 401 even WITH an entitlement — fan's shape")
        void entitledWithoutTenantIdRefusedWhenOff() throws Exception {
            TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("scm").build();
            authenticate(jwt(null, List.of("scm")));
            enforcer.doFilter(get("/api/x"), response, chain);

            assertThat(chain.called).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("ON: an absent tenant_id with NO entitlement is still 401 — the relaxation "
                + "widens, it does not disable the check")
        void absentTenantWithoutEntitlementStill401() throws Exception {
            TenantClaimEnforcer enforcer =
                    TenantClaimEnforcer.forTenant("scm").trustEntitledDomains().build();
            authenticate(jwt(null, List.of("erp")));   // entitled, but to somewhere else
            enforcer.doFilter(get("/api/x"), response, chain);

            assertThat(chain.called).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }

    // ---------------------------------------------------------------------
    // The enforcer is the INNER layer of a defence in depth whose outer layer is
    // TenantClaimValidator, running at decode time in the same service. Two layers that
    // disagree are not defence in depth — the inner one refusing what the outer one just
    // admitted is a decode-pass / filter-block split, and it is the failure the thirteen
    // hand-written copies each took care to avoid. TASK-MONO-383 found the canonical class
    // had reintroduced it; this pins it shut.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("agrees with the decoder it backs up (TenantClaimValidator)")
    class AgreesWithTheDecoder {

        /** The wiring erp, finance and scm all use: wildcard on, entitlement trusted. */
        private final TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("scm")
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();

        private final TenantClaimValidator validator = TenantClaimValidator.forTenant("scm")
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();

        @Test
        @DisplayName("every token the validator admits, the enforcer admits")
        void noTokenIsAdmittedByTheDecoderAndRefusedByTheFilter() throws Exception {
            List<Jwt> tokens = List.of(
                    jwt("scm", null),                          // exact
                    jwt(TenantClaimValidator.WILDCARD_TENANT, null),  // wildcard
                    jwt("erp", List.of("scm")),                // entitled, wrong slug
                    jwt(null, List.of("scm")),                 // entitled, NO slug  <-- MONO-383
                    jwt("", List.of("scm")));                  // entitled, blank slug

            for (Jwt token : tokens) {
                assertThat(validator.validate(token).hasErrors())
                        .as("precondition: the decoder admits %s", token.getClaims())
                        .isFalse();

                MockHttpServletResponse res = new MockHttpServletResponse();
                RecordingChain ch = new RecordingChain();
                SecurityContextHolder.getContext()
                        .setAuthentication(new JwtAuthenticationToken(token));
                enforcer.doFilter(get("/api/x"), res, ch);

                assertThat(ch.called)
                        .as("the decoder admitted %s; the enforcer behind it must not refuse it "
                                + "(got %d)", token.getClaims(), res.getStatus())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("every token the validator refuses, the enforcer refuses")
        void noTokenIsRefusedByTheDecoderAndAdmittedByTheFilter() throws Exception {
            List<Jwt> tokens = List.of(
                    jwt("erp", null),                    // wrong tenant, no entitlement
                    jwt("erp", List.of("finance")),      // entitled elsewhere
                    jwt(null, null),                     // no tenant context at all
                    jwt("", null));                      // blank

            for (Jwt token : tokens) {
                assertThat(validator.validate(token).hasErrors())
                        .as("precondition: the decoder refuses %s", token.getClaims())
                        .isTrue();

                MockHttpServletResponse res = new MockHttpServletResponse();
                RecordingChain ch = new RecordingChain();
                SecurityContextHolder.getContext()
                        .setAuthentication(new JwtAuthenticationToken(token));
                enforcer.doFilter(get("/api/x"), res, ch);

                assertThat(ch.called)
                        .as("the decoder refused %s; the enforcer must not let it through",
                                token.getClaims())
                        .isFalse();
            }
        }
    }

    // ---------------------------------------------------------------------
    // The wire contract the thirteen copies all shared.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("the wire contract")
    class WireContract {

        private final TenantClaimEnforcer enforcer = TenantClaimEnforcer.forTenant("scm").build();

        @Test
        @DisplayName("an absent tenant_id is 401 UNAUTHORIZED — \"any tenant\" is not \"no tenant\"")
        void absentTenantIs401() throws Exception {
            authenticate(jwt(null, null));
            enforcer.doFilter(get("/api/x"), response, chain);

            assertThat(chain.called).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(bodyOf(response).get("code").asText())
                    .isEqualTo(TenantClaimEnforcer.CODE_UNAUTHORIZED);
        }

        @Test
        @DisplayName("a cross-tenant token is 403, NOT 401 — the token is signature-valid")
        void crossTenantIs403NotUnauthorized() throws Exception {
            authenticate(jwt("erp", null));
            enforcer.doFilter(get("/api/x"), response, chain);

            assertThat(response.getStatus())
                    .as("401 would tell a client with a perfectly valid token to re-authenticate — "
                            + "a lie it can loop on forever")
                    .isEqualTo(HttpStatus.FORBIDDEN.value());
            JsonNode body = bodyOf(response);
            assertThat(body.get("code").asText())
                    .isEqualTo(TenantClaimEnforcer.CODE_TENANT_FORBIDDEN);
            assertThat(body.get("message").asText()).contains("erp");
            assertThat(body.has("timestamp")).isTrue();
        }

        @Test
        @DisplayName("a request with no JWT is passed through — authorization is Spring Security's job")
        void nonJwtRequestPassesThrough() throws Exception {
            enforcer.doFilter(get("/api/x"), response, chain);
            assertThat(chain.called).isTrue();
        }

        @Test
        @DisplayName("the filter order is the one all thirteen copies agreed on")
        void orderIsPinned() {
            assertThat(enforcer.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 100);
        }
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static Jwt jwt(String tenantId, List<String> entitledDomains) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "user-1");
        if (tenantId != null) {
            builder.claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        }
        if (entitledDomains != null) {
            builder.claim(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, entitledDomains);
        }
        return builder.build();
    }

    /** {@code entitled_domains} as a bare String — the shape that must fail closed, not throw. */
    private static Jwt jwtWithRawClaim(String tenantId, String malformedEntitled) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(Map.of(
                        "sub", "user-1",
                        TenantClaimValidator.CLAIM_TENANT_ID, tenantId,
                        TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, malformedEntitled)))
                .build();
    }

    private void assertForbidden() {
        assertThat(chain.called).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    private static JsonNode bodyOf(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString());
    }

    /** {@link MockFilterChain} allows a single invocation; this one just records. */
    private static final class RecordingChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            this.called = true;
        }
    }
}
