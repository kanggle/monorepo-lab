package com.example.finance.account.presentation.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TenantClaimEnforcer} — defense-in-depth fail-closed
 * cross-tenant gate (architecture.md Failure Mode #3).
 */
class TenantClaimEnforcerTest {

    private final TenantClaimEnforcer filter = new TenantClaimEnforcer("finance");

    private void authenticate(String tenant) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("tenant_id", tenant, "sub", "u"));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt));
    }

    @Test
    @DisplayName("finance tenant passes through the chain")
    void financePasses() throws Exception {
        authenticate("finance");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(request, resp, chain);
        verify(chain).doFilter(request, resp);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("cross-tenant → 403 TENANT_FORBIDDEN, chain NOT invoked")
    void crossTenantBlocked() throws Exception {
        authenticate("wms");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req(), resp, chain);
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("TENANT_FORBIDDEN");
        verify(chain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("public actuator path bypasses the filter")
    void publicPathBypassed() {
        MockHttpServletRequest health = new MockHttpServletRequest();
        health.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(health)).isTrue();
    }

    private static MockHttpServletRequest req() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI("/api/finance/accounts/acc-1");
        return r;
    }
}
