package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantIntrospectionCustomizer}.
 *
 * <p>Verifies that {@code tenant_id} and {@code tenant_type} extension claims are
 * correctly appended to the standard RFC 7662 introspection response payload,
 * and that the customizer does not break when tenant claims are absent.
 *
 * <p>TASK-BE-251 Phase 2c.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TenantIntrospectionCustomizerTest {

    private TenantIntrospectionCustomizer customizer;

    @BeforeEach
    void setUp() {
        customizer = new TenantIntrospectionCustomizer();
    }

    // -----------------------------------------------------------------------
    // 1. Tenant claims present in JWT → enriched in introspect response
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tenant_id + tenant_type claims in JWT → included in introspect response")
    void tenantClaims_presentInJwt_enrichedInResponse() throws Exception {
        // Build introspection result with tenant claims (simulates TenantClaimTokenCustomizer output)
        OAuth2TokenIntrospection introspection = OAuth2TokenIntrospection.builder()
                .active(true)
                .clientId("test-client")
                .username("test-user")
                .subject("account-uuid-001")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(1800))
                .claim("tenant_id", "fan-platform")
                .claim("tenant_type", "B2C")
                .build();

        OAuth2TokenIntrospectionAuthenticationToken authToken =
                buildIntrospectionAuthToken(introspection);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customizer.onAuthenticationSuccess(request, response, authToken);

        assertThat(response.getStatus()).isEqualTo(200);
        String body = response.getContentAsString();
        assertThat(body).contains("\"active\":true");
        assertThat(body).contains("\"tenant_id\"");
        assertThat(body).contains("fan-platform");
        assertThat(body).contains("\"tenant_type\"");
        assertThat(body).contains("B2C");
    }

    // -----------------------------------------------------------------------
    // 2. tenant claims absent → no NPE, response still valid
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tenant claims absent in JWT → response valid without tenant fields")
    void tenantClaims_absent_responseStillValid() throws Exception {
        OAuth2TokenIntrospection introspection = OAuth2TokenIntrospection.builder()
                .active(true)
                .clientId("no-tenant-client")
                .build();

        OAuth2TokenIntrospectionAuthenticationToken authToken =
                buildIntrospectionAuthToken(introspection);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Must not throw
        customizer.onAuthenticationSuccess(request, response, authToken);

        assertThat(response.getStatus()).isEqualTo(200);
        String body = response.getContentAsString();
        assertThat(body).contains("\"active\":true");
        // tenant_id must NOT be present when not in JWT claims
        assertThat(body).doesNotContain("\"tenant_id\"");
        assertThat(body).doesNotContain("\"tenant_type\"");
    }

    // -----------------------------------------------------------------------
    // 3. active=false token → active=false in response (revoked token)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("inactive token (revoked/expired) → active=false in introspect response")
    void inactiveToken_activeIsFalse() throws Exception {
        OAuth2TokenIntrospection introspection = OAuth2TokenIntrospection.builder()
                .active(false)
                .build();

        OAuth2TokenIntrospectionAuthenticationToken authToken =
                buildIntrospectionAuthToken(introspection);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customizer.onAuthenticationSuccess(request, response, authToken);

        assertThat(response.getStatus()).isEqualTo(200);
        String body = response.getContentAsString();
        assertThat(body).contains("\"active\":false");
    }

    // -----------------------------------------------------------------------
    // 4. Standard RFC 7662 fields are preserved
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("standard RFC 7662 fields (sub, exp, iat, client_id, scope) are preserved in enriched response")
    void standardFields_preserved_inEnrichedResponse() throws Exception {
        Instant issuedAt = Instant.now().minusSeconds(30);
        Instant expiresAt = Instant.now().plusSeconds(1770);

        OAuth2TokenIntrospection introspection = OAuth2TokenIntrospection.builder()
                .active(true)
                .clientId("service-client")
                .username("user@example.com")
                .subject("acct-123")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .scopes(s -> s.add("account.read"))
                .claim("tenant_id", "wms")
                .claim("tenant_type", "B2B_ENTERPRISE")
                .build();

        OAuth2TokenIntrospectionAuthenticationToken authToken =
                buildIntrospectionAuthToken(introspection);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customizer.onAuthenticationSuccess(request, response, authToken);

        String body = response.getContentAsString();
        assertThat(body).contains("\"active\":true");
        assertThat(body).contains("service-client");
        assertThat(body).contains("acct-123");
        assertThat(body).contains("account.read");
        assertThat(body).contains("wms");
        assertThat(body).contains("B2B_ENTERPRISE");
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /**
     * Builds a minimal {@link OAuth2TokenIntrospectionAuthenticationToken} for testing.
     * The token wraps the given {@link OAuth2TokenIntrospection} as the "claims" result.
     */
    private OAuth2TokenIntrospectionAuthenticationToken buildIntrospectionAuthToken(
            OAuth2TokenIntrospection introspection) {
        // OAuth2TokenIntrospectionAuthenticationToken(token, clientPrincipal, tokenClaims)
        // We use the 3-arg constructor that accepts tokenClaims directly.
        return new OAuth2TokenIntrospectionAuthenticationToken(
                "some-token-value",
                mock(org.springframework.security.core.Authentication.class),
                introspection);
    }
}
