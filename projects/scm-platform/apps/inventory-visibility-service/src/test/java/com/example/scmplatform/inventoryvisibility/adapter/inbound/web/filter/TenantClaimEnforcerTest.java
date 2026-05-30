package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TenantClaimEnforcer} — defense-in-depth fail-closed
 * cross-tenant gate with entitlement-trust dual-accept (ADR-MONO-019 § D5).
 * This service has no decode-time validator, so {@link TenantClaimEnforcer#isEntitled}
 * is a local helper; the cases below exercise the dual-accept branch and its
 * fail-closed claim-shape handling.
 */
class TenantClaimEnforcerTest {

    private final TenantClaimEnforcer filter = new TenantClaimEnforcer("scm");

    private void authenticate(Map<String, Object> claims) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    @DisplayName("scm tenant passes through the chain")
    void scmPasses() throws Exception {
        authenticate(Map.of("tenant_id", "scm", "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(request, resp, chain);
        verify(chain).doFilter(request, resp);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("cross-tenant (wms) → 403 TENANT_FORBIDDEN, chain NOT invoked")
    void crossTenantBlocked() throws Exception {
        authenticate(Map.of("tenant_id", "wms", "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req(), resp, chain);
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("TENANT_FORBIDDEN");
        verify(chain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("entitlement-trust: tenant_id=acme + entitled_domains=[scm] passes through")
    void entitledCrossTenantPasses() throws Exception {
        authenticate(Map.of("tenant_id", "acme", "entitled_domains", List.of("scm"), "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(request, resp, chain);
        verify(chain).doFilter(request, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("non-entitled: tenant_id=acme + entitled_domains=[wms] → 403, chain NOT invoked")
    void nonEntitledCrossTenantBlocked() throws Exception {
        authenticate(Map.of("tenant_id", "acme", "entitled_domains", List.of("wms"), "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req(), resp, chain);
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("TENANT_FORBIDDEN");
        verify(chain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("entitled_domains containing scm grants even when tenant_id absent")
    void entitledWithoutTenantIdPasses() throws Exception {
        authenticate(Map.of("entitled_domains", List.of("scm"), "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(request, resp, chain);
        verify(chain).doFilter(request, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("wildcard (*) SUPER_ADMIN passes through")
    void wildcardPasses() throws Exception {
        authenticate(Map.of("tenant_id", "*", "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(request, resp, chain);
        verify(chain).doFilter(request, resp);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("isEntitled claim-shape safety: null / non-list / non-string element → false")
    void isEntitledClaimShapeSafety() {
        Jwt acmeNoClaim = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("tenant_id", "acme", "sub", "u"));
        Jwt nonList = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("tenant_id", "acme", "entitled_domains", "scm", "sub", "u"));
        Jwt nonStringElement = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("tenant_id", "acme", "entitled_domains", List.of(42), "sub", "u"));
        assertThat(TenantClaimEnforcer.isEntitled(null, "scm")).isFalse();
        assertThat(TenantClaimEnforcer.isEntitled(acmeNoClaim, null)).isFalse();
        assertThat(TenantClaimEnforcer.isEntitled(acmeNoClaim, "scm")).isFalse();
        assertThat(TenantClaimEnforcer.isEntitled(nonList, "scm")).isFalse();
        assertThat(TenantClaimEnforcer.isEntitled(nonStringElement, "scm")).isFalse();
    }

    @Test
    @DisplayName("actuator path bypasses the filter")
    void publicPathBypassed() {
        MockHttpServletRequest health = new MockHttpServletRequest();
        health.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(health)).isTrue();
    }

    private static MockHttpServletRequest req() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI("/api/inventory-visibility/snapshots");
        return r;
    }
}
