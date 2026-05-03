package com.example.fanplatform.community.presentation.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("TenantClaimEnforcer — defense-in-depth fail-closed")
class TenantClaimEnforcerTest {

    private final TenantClaimEnforcer enforcer = new TenantClaimEnforcer("fan-platform");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("tenant_id=fan-platform → chain 진행")
    void fanPlatformPassesThrough() throws IOException, ServletException {
        setAuthWithTenant("fan-platform");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        enforcer.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("tenant_id=wms → 403 TENANT_FORBIDDEN, chain 진입 차단")
    void wmsRejectedAt403() throws IOException, ServletException {
        setAuthWithTenant("wms");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        enforcer.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TENANT_FORBIDDEN");
    }

    @Test
    @DisplayName("tenant_id=* (SUPER_ADMIN) → chain 진행")
    void wildcardSuperAdminPasses() throws IOException, ServletException {
        setAuthWithTenant("*");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        enforcer.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("/actuator/health → tenant 검사 skip (whitelisted)")
    void healthIsSkipped() throws IOException, ServletException {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        enforcer.doFilter(request, response, chain);
        verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("/actuator/health/liveness → tenant 검사 skip (prefix wildcard)")
    void healthLivenessSkipped() throws IOException, ServletException {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/liveness");
        MockHttpServletResponse response = new MockHttpServletResponse();

        enforcer.doFilter(request, response, chain);
        verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("/actuator/info, /actuator/prometheus → skip (whitelisted)")
    void infoAndPrometheusSkipped() throws IOException, ServletException {
        for (String path : new String[]{"/actuator/info", "/actuator/prometheus"}) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            enforcer.doFilter(request, response, chain);
            verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        }
    }

    @Test
    @DisplayName("/actuator/env → NOT whitelisted: shouldNotFilter=false, missing JWT → 401")
    void actuatorEnvNotSkipped() throws Exception {
        // No SecurityContext authentication is set — the filter must still
        // execute the tenant-check path. The defensive contract here is:
        //   1) shouldNotFilter MUST return false for unwhitelisted actuator
        //      paths (defense-in-depth: a future leak of /actuator/env is
        //      caught by the JWT requirement, not silently bypassed);
        //   2) without a JwtAuthenticationToken in the context the filter
        //      flows through to chain.doFilter (the actual rejection happens
        //      in SecurityConfig anyRequest().denyAll()).
        // Assertion (1) is the load-bearing check.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/env");
        java.lang.reflect.Method m = enforcer.getClass()
                .getDeclaredMethod("shouldNotFilter", HttpServletRequest.class);
        m.setAccessible(true);
        boolean skip = (boolean) m.invoke(enforcer, request);
        assertThat(skip)
                .as("/actuator/env must NOT bypass the tenant filter")
                .isFalse();
    }

    private static void setAuthWithTenant(String tenantId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://gap.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("tenant_id", tenantId)
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
