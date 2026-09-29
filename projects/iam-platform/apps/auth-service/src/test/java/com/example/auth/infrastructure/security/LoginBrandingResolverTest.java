package com.example.auth.infrastructure.security;

import com.example.auth.application.port.TenantTypePort;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-613 (ADR-007) — {@link LoginBranding} validation and {@link LoginBrandingResolver}'s
 * trust rule.
 *
 * <p>The resolver is driven through a REAL {@link SavedRequestTenantResolver} and a real
 * {@link HttpSessionRequestCache}: a mocked {@code initiatingClient} would accept any shortcut a
 * future edit adds, and the property under test (invariant 1 — branding is never read from the
 * request) lives exactly in which path the {@code client_id} comes from.
 */
class LoginBrandingResolverTest {

    private static final String GAP_CLIENT = "fan-platform-user-flow-client";
    private static final String STORE_CLIENT = "ecommerce-web-store-client";

    private static RegisteredClient client(String clientId, Map<String, Object> branding) {
        ClientSettings.Builder settings = ClientSettings.builder()
                .setting(OAuthClientMapper.SETTING_TENANT_ID, "t")
                .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C_CONSUMER");
        branding.forEach(settings::setting);
        return RegisteredClient.withId("id-" + clientId)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://app.local/callback")
                .scope("openid")
                .clientSettings(settings.build())
                .build();
    }

    private static Map<String, Object> gapBranding() {
        return Map.of(
                OAuthClientMapper.SETTING_BRANDING_SERVICE_NAME, "GAP",
                OAuthClientMapper.SETTING_BRANDING_TITLE, "GAP로 로그인",
                OAuthClientMapper.SETTING_BRANDING_DESCRIPTION, "GAP으로 안전하게 로그인합니다",
                OAuthClientMapper.SETTING_BRANDING_PRIMARY_COLOR, "#9333ea");
    }

    /** A {@code /login} request whose session holds a saved request for {@code savedPath}. */
    private static MockHttpServletRequest loginAfterSaved(String savedPath, String clientId) {
        MockHttpServletRequest original = new MockHttpServletRequest("GET", savedPath);
        original.setServerName("iam.local");
        original.setParameter("client_id", clientId);
        original.setQueryString("response_type=code&client_id=" + clientId);
        new HttpSessionRequestCache().saveRequest(original, new MockHttpServletResponse());

        MockHttpServletRequest login = new MockHttpServletRequest("GET", "/login");
        login.setSession(original.getSession());
        return login;
    }

    private static LoginBrandingResolver resolverOver(RegisteredClientRepository repository) {
        return new LoginBrandingResolver(
                new SavedRequestTenantResolver(repository, mock(TenantTypePort.class)));
    }

    @Nested
    @DisplayName("resolver — which client's branding a page may show")
    class Resolver {

        @Test
        @DisplayName("a saved /oauth2/authorize from the fan client → GAP branding")
        void savedAuthorizeRequestPicksThatClient() {
            RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
            when(repository.findByClientId(GAP_CLIENT)).thenReturn(client(GAP_CLIENT, gapBranding()));

            LoginBranding branding = resolverOver(repository).resolve(
                    loginAfterSaved("/oauth2/authorize", GAP_CLIENT), new MockHttpServletResponse());

            assertThat(branding).isEqualTo(new LoginBranding(
                    "GAP", "GAP로 로그인", "GAP으로 안전하게 로그인합니다", null, "#9333ea"));
        }

        @Test
        @DisplayName("no saved request → the IAM default (ADR-007 D3 as accepted)")
        void directVisitIsTheDefault() {
            RegisteredClientRepository repository = mock(RegisteredClientRepository.class);

            LoginBranding branding = resolverOver(repository).resolve(
                    new MockHttpServletRequest("GET", "/login"), new MockHttpServletResponse());

            assertThat(branding).isEqualTo(LoginBranding.DEFAULT);
            assertThat(branding.title()).isEqualTo("IAM 로그인");
        }

        @Test
        @DisplayName("BITE (invariant 1) — client_id on the CURRENT request is ignored")
        void currentRequestClientIdIsIgnored() {
            RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
            when(repository.findByClientId(GAP_CLIENT)).thenReturn(client(GAP_CLIENT, gapBranding()));

            MockHttpServletRequest login = new MockHttpServletRequest("GET", "/login");
            login.setParameter("client_id", GAP_CLIENT);
            login.setQueryString("client_id=" + GAP_CLIENT);

            assertThat(resolverOver(repository).resolve(login, new MockHttpServletResponse()))
                    .isEqualTo(LoginBranding.DEFAULT);
            verify(repository, never()).findByClientId(anyString());
        }

        @Test
        @DisplayName("BITE (invariant 1) — a saved request that is not /oauth2/authorize is not trusted")
        void savedNonAuthorizeRequestIsIgnored() {
            RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
            when(repository.findByClientId(GAP_CLIENT)).thenReturn(client(GAP_CLIENT, gapBranding()));

            LoginBranding branding = resolverOver(repository).resolve(
                    loginAfterSaved("/some/page", GAP_CLIENT), new MockHttpServletResponse());

            assertThat(branding).isEqualTo(LoginBranding.DEFAULT);
        }

        @Test
        @DisplayName("an unknown client → the default, not an error")
        void unknownClientIsTheDefault() {
            RegisteredClientRepository repository = mock(RegisteredClientRepository.class);

            LoginBranding branding = resolverOver(repository).resolve(
                    loginAfterSaved("/oauth2/authorize", "no-such-client"),
                    new MockHttpServletResponse());

            assertThat(branding).isEqualTo(LoginBranding.DEFAULT);
        }

        @Test
        @DisplayName("a registered client with no branding keys → the default")
        void unbrandedClientIsTheDefault() {
            RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
            when(repository.findByClientId(STORE_CLIENT)).thenReturn(client(STORE_CLIENT, Map.of()));

            LoginBranding branding = resolverOver(repository).resolve(
                    loginAfterSaved("/oauth2/authorize", STORE_CLIENT), new MockHttpServletResponse());

            assertThat(branding).isEqualTo(LoginBranding.DEFAULT);
        }
    }

    @Nested
    @DisplayName("LoginBranding — per-key validation and fallback")
    class Values {

        @Test
        @DisplayName("defaults: IAM · 'IAM 로그인' · no description · no logo · the pre-branding colour")
        void defaults() {
            assertThat(LoginBranding.DEFAULT).isEqualTo(
                    new LoginBranding("IAM", "IAM 로그인", null, null, "#2563eb"));
            assertThat(LoginBranding.DEFAULT.signupTitle()).isEqualTo("IAM 회원가입");
        }

        @Test
        @DisplayName("a service name alone derives the title; the other keys fall back one by one")
        void titleDerivesFromServiceName() {
            LoginBranding b = LoginBranding.of("GAP", null, "  ", null, null);

            assertThat(b).isEqualTo(new LoginBranding("GAP", "GAP 로그인", null, null, "#2563eb"));
            assertThat(b.signupTitle()).isEqualTo("GAP 회원가입");
        }

        @Test
        @DisplayName("values are trimmed; non-string values count as absent")
        void trimmedAndTypeChecked() {
            LoginBranding b = LoginBranding.of("  Global Account ", 42, true, null, " #1a1a2e ");

            assertThat(b).isEqualTo(new LoginBranding(
                    "Global Account", "Global Account 로그인", null, null, "#1a1a2e"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"red", "#fff", "#12345G", "#1234567", "1a1a2e",
                "#171717;background:url(x)", "expression(alert(1))"})
        @DisplayName("BITE (invariant 2) — a colour that is not exactly #RRGGBB falls back")
        void malformedColourFallsBack(String colour) {
            assertThat(LoginBranding.of("GAP", null, null, null, colour).primaryColor())
                    .isEqualTo(LoginBranding.DEFAULT_PRIMARY_COLOR);
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://evil.example/logo.svg", "//evil.example/x.svg",
                "../console", "console.svg", "Console", "javascript:alert(1)", "fan"})
        @DisplayName("BITE (invariant 2) — a logo outside the allowlist renders no logo")
        void logoOutsideAllowlistIsDropped(String logo) {
            assertThat(LoginBranding.of("IAM", null, null, logo, null).logo()).isNull();
        }

        @Test
        @DisplayName("an allowlisted logo name is kept")
        void allowlistedLogoIsKept() {
            assertThat(LoginBranding.of("IAM", null, null, "console", null).logo())
                    .isEqualTo("console");
        }
    }
}
