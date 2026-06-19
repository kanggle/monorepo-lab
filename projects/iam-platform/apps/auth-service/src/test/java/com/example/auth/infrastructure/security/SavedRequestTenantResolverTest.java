package com.example.auth.infrastructure.security;

import com.example.auth.application.port.TenantTypePort;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SavedRequestTenantResolver} (TASK-BE-396, ADR-006 option 1).
 *
 * <p>Drives a real {@link HttpSessionRequestCache} (the production resolver uses
 * its own instance) by saving a {@code /oauth2/authorize} request onto a
 * {@link MockHttpServletRequest}'s session, then asserts the resolver reads the
 * {@code client_id} → client tenant, and falls back to the default tenant when
 * the saved request is absent.
 */
@ExtendWith(MockitoExtension.class)
class SavedRequestTenantResolverTest {

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    @Mock
    private TenantTypePort tenantTypePort;

    private RegisteredClient ecommerceClient() {
        return RegisteredClient.withId("id-1")
                .clientId("ecommerce-web-store-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://web-store.local/callback")
                .scope("openid")
                .clientSettings(ClientSettings.builder()
                        .setting(OAuthClientMapper.SETTING_TENANT_ID, "ecommerce")
                        .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C_CONSUMER")
                        .build())
                .build();
    }

    /**
     * Saves a {@code GET /oauth2/authorize?client_id=...} request into the request
     * cache so the resolver (which builds its own HttpSessionRequestCache reading the
     * same session) can retrieve it.
     */
    private MockHttpServletRequest savedAuthorizeRequest(String clientId) {
        // First request: the original /oauth2/authorize that gets saved.
        MockHttpServletRequest original = new MockHttpServletRequest("GET", "/oauth2/authorize");
        original.setServerName("iam.local");
        original.setScheme("http");
        original.setServerPort(80);
        original.setParameter("client_id", clientId);
        original.setParameter("response_type", "code");
        // MockHttpServletRequest does not derive the query string from params — set it
        // explicitly so DefaultSavedRequest.getRedirectUrl() carries the OAuth params
        // (a real servlet container populates getQueryString()).
        original.setQueryString("response_type=code&client_id=" + clientId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new HttpSessionRequestCache().saveRequest(original, response);

        // Second request: the callback, carrying the SAME session.
        MockHttpServletRequest callback = new MockHttpServletRequest("GET",
                "/login/oauth/google/callback");
        callback.setSession(original.getSession());
        return callback;
    }

    @Test
    @DisplayName("saved /oauth2/authorize with client_id → resolves the client's tenant + redirect URL")
    void savedRequestWithClientId_resolvesClientTenant() {
        when(registeredClientRepository.findByClientId("ecommerce-web-store-client"))
                .thenReturn(ecommerceClient());
        // Resolved tenant comes from the client settings on this path — the
        // TenantTypeResolver fallback is NOT consulted, so it is left unstubbed.
        SavedRequestTenantResolver resolver =
                new SavedRequestTenantResolver(registeredClientRepository, tenantTypePort);

        HttpServletRequest callback = savedAuthorizeRequest("ecommerce-web-store-client");
        HttpServletResponse response = new MockHttpServletResponse();

        SavedRequestTenantResolver.Resolution resolution = resolver.resolve(callback, response);

        assertThat(resolution.tenantId()).isEqualTo("ecommerce");
        assertThat(resolution.tenantType()).isEqualTo("B2C_CONSUMER");
        assertThat(resolution.redirectUrl()).contains("/oauth2/authorize");
        assertThat(resolution.redirectUrl()).contains("client_id=ecommerce-web-store-client");
    }

    @Test
    @DisplayName("no saved request → default tenant fallback, null redirect URL")
    void noSavedRequest_fallsBackToDefaultTenant() {
        // TASK-BE-407: the fallback now derives tenant_type via the resolver, which for
        // DEFAULT_TENANT_ID returns the pre-seeded DEFAULT_TENANT_TYPE (no network).
        when(tenantTypePort.resolve(TenantContext.DEFAULT_TENANT_ID))
                .thenReturn(TenantContext.DEFAULT_TENANT_TYPE);
        SavedRequestTenantResolver resolver =
                new SavedRequestTenantResolver(registeredClientRepository, tenantTypePort);

        MockHttpServletRequest callback = new MockHttpServletRequest("GET",
                "/login/oauth/google/callback");
        HttpServletResponse response = new MockHttpServletResponse();

        SavedRequestTenantResolver.Resolution resolution = resolver.resolve(callback, response);

        assertThat(resolution.tenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
        assertThat(resolution.tenantType()).isEqualTo(TenantContext.DEFAULT_TENANT_TYPE);
        assertThat(resolution.redirectUrl()).isNull();
    }

    @Test
    @DisplayName("saved request but client not found → default tenant fallback (redirect URL still present)")
    void clientNotFound_fallsBackToDefaultTenant() {
        when(registeredClientRepository.findByClientId("unknown-client")).thenReturn(null);
        when(tenantTypePort.resolve(TenantContext.DEFAULT_TENANT_ID))
                .thenReturn(TenantContext.DEFAULT_TENANT_TYPE);
        SavedRequestTenantResolver resolver =
                new SavedRequestTenantResolver(registeredClientRepository, tenantTypePort);

        HttpServletRequest callback = savedAuthorizeRequest("unknown-client");
        HttpServletResponse response = new MockHttpServletResponse();

        SavedRequestTenantResolver.Resolution resolution = resolver.resolve(callback, response);

        assertThat(resolution.tenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
        assertThat(resolution.redirectUrl()).contains("/oauth2/authorize");
    }
}
