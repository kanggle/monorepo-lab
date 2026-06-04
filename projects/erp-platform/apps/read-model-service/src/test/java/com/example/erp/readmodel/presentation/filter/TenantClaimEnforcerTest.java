package com.example.erp.readmodel.presentation.filter;

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
 * Unit tests for the {@link TenantClaimEnforcer} servlet filter — defense-in-
 * depth fail-closed cross-tenant gate with entitlement-trust dual-accept.
 */
class TenantClaimEnforcerTest {

    private final TenantClaimEnforcer filter = new TenantClaimEnforcer("erp");

    private void authenticate(Map<String, Object> claims) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    @DisplayName("erp tenant passes through")
    void erpPasses() throws Exception {
        authenticate(Map.of("tenant_id", "erp", "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(request, resp, chain);
        verify(chain).doFilter(request, resp);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("cross-tenant (scm) → 403 TENANT_FORBIDDEN, chain NOT invoked")
    void crossTenantBlocked() throws Exception {
        authenticate(Map.of("tenant_id", "scm", "sub", "u"));
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
    @DisplayName("entitlement-trust: tenant_id=acme + entitled_domains=[erp] passes through")
    void entitledCrossTenantPasses() throws Exception {
        authenticate(Map.of("tenant_id", "acme", "entitled_domains", List.of("erp"), "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(request, resp, chain);
        verify(chain).doFilter(request, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("non-entitled cross-tenant → 403, chain NOT invoked")
    void nonEntitledBlocked() throws Exception {
        authenticate(Map.of("tenant_id", "acme", "entitled_domains", List.of("scm"), "sub", "u"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req(), resp, chain);
        assertThat(resp.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
    @DisplayName("actuator path bypasses the filter")
    void publicPathBypassed() {
        MockHttpServletRequest health = new MockHttpServletRequest();
        health.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(health)).isTrue();
    }

    private static MockHttpServletRequest req() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI("/api/erp/read-model/employees");
        return r;
    }
}
