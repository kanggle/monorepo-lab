package com.example.fanplatform.membership.presentation.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TenantClaimEnforcerTest {

    private final TenantClaimEnforcer filter = new TenantClaimEnforcer("fan-platform");
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String tenantId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("acc1").claim("tenant_id", tenantId)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    @DisplayName("matching tenant passes through the chain")
    void matchingTenantPasses() throws Exception {
        authenticate("fan-platform");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/fan/memberships");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("cross-tenant → 403 TENANT_FORBIDDEN, chain not invoked")
    void crossTenantRejected() throws Exception {
        authenticate("wms");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/fan/memberships");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
        JsonNode body = mapper.readTree(res.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    @DisplayName("/internal/** is skipped (workload-identity carries no tenant_id)")
    void internalPathSkipped() throws Exception {
        authenticate("wms"); // would be rejected on an end-user path
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/membership/access");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("public actuator path is skipped")
    void publicPathSkipped() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }
}
