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
