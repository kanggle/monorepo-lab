package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-605 — {@link AuthorizeSessionTenantGate}: which authenticated sessions may be reused
 * at {@code /oauth2/authorize} for which client.
 *
 * <p>Each cell runs the real filter and reads the principal the NEXT filter (SAS's authorization
 * endpoint, in production) would see. The end-to-end proof — the browser is sent to
 * {@code /login}, logs in with the client-tenant credential and gets a token of that tenant — is
 * {@code SsoTenantGateIntegrationTest} (Testcontainers, CI-authoritative).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AuthorizeSessionTenantGateTest {

    private static final String AUTHORIZE = "/oauth2/authorize";

    @Mock
    RegisteredClientRepository registeredClientRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("같은 테넌트(ecommerce 세션 → ecommerce client) → 세션 그대로 통과")
    void sameTenant_passesUntouched() throws Exception {
        stubClient("store", "ecommerce");
        Authentication session = principal("ecommerce");

        assertThat(runAuthorize(session, "store")).isSameAs(session);
    }

    @Test
    @DisplayName("다른 소비자 테넌트(ecommerce 세션 → fan-platform client) → 이 요청은 미인증 = 재인증")
    void otherConsumerTenant_requiresReauthentication() throws Exception {
        stubClient("fan", "fan-platform");

        assertThat(runAuthorize(principal("ecommerce"), "fan")).isNull();
    }

    @Test
    @DisplayName("콘솔 client(iam) 는 교차 테넌트 세션도 통과 — ADR-MONO-044 D5 운영자")
    void consoleClient_exempt() throws Exception {
        stubClient("platform-console-web", "iam");
        Authentication session = principal("fan-platform");

        assertThat(runAuthorize(session, "platform-console-web")).isSameAs(session);
    }

    @Test
    @DisplayName("플랫폼 스코프 '*' 세션 → 소비자 client 는 재인증")
    void platformScopeOnConsumerClient_requiresReauthentication() throws Exception {
        stubClient("store", "ecommerce");

        assertThat(runAuthorize(principal("*"), "store")).isNull();
    }

    @Test
    @DisplayName("플랫폼 스코프 '*' 세션 → 콘솔 client 는 통과")
    void platformScopeOnConsole_passes() throws Exception {
        stubClient("platform-console-web", "iam");
        Authentication session = principal("*");

        assertThat(runAuthorize(session, "platform-console-web")).isSameAs(session);
    }

    @Test
    @DisplayName("tenant_type 이 없는 principal → 토큰 claim 규칙대로 client 테넌트로 간주 → 통과")
    void principalWithoutTenantPair_isTheClientTenant() throws Exception {
        stubClient("fan", "fan-platform");
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, "ecommerce");
        UsernamePasswordAuthenticationToken session = new UsernamePasswordAuthenticationToken(
                "someone@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        session.setDetails(details);

        assertThat(runAuthorize(session, "fan")).isSameAs(session);
    }

    @Test
    @DisplayName("client 테넌트 설정 앞뒤 공백은 무시한다(SavedRequestTenantResolver 와 같은 판독)")
    void clientTenantIsTrimmed() throws Exception {
        stubClient("store", " ecommerce ");
        Authentication session = principal("ecommerce");

        assertThat(runAuthorize(session, "store")).isSameAs(session);
    }

    @Test
    @DisplayName("모르는 client · 테넌트 설정 없는 client → 판정하지 않는다(SAS 가 기존대로 답한다)")
    void unknownClientOrNoTenantSetting_passes() throws Exception {
        when(registeredClientRepository.findByClientId("ghost")).thenReturn(null);
        Authentication session = principal("ecommerce");
        assertThat(runAuthorize(session, "ghost")).isSameAs(session);

        when(registeredClientRepository.findByClientId("bare")).thenReturn(client("bare", null));
        assertThat(runAuthorize(session, "bare")).isSameAs(session);
    }

    @Test
    @DisplayName("미인증 · 익명 · client_id 없음 → client 조회조차 하지 않는다")
    void nothingToGate_noLookup() throws Exception {
        assertThat(runAuthorize(null, "store")).isNull();
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(runAuthorize(anonymous, "store")).isSameAs(anonymous);
        Authentication session = principal("ecommerce");
        assertThat(runAuthorize(session, null)).isSameAs(session);

        verifyNoInteractions(registeredClientRepository);
    }

    @Test
    @DisplayName("authorize 가 아닌 경로(/oauth2/token 등)에는 게이트가 없다")
    void otherPaths_notGated() throws Exception {
        AuthorizeSessionTenantGate gate = new AuthorizeSessionTenantGate(AUTHORIZE, registeredClientRepository);
        Authentication session = principal("ecommerce");
        for (String path : List.of("/oauth2/token", "/oauth2/userinfo", "/oauth2/revoke", "/login")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            request.setParameter("client_id", "fan");
            assertThat(run(gate, request, session)).as(path).isSameAs(session);
        }
        verifyNoInteractions(registeredClientRepository);
    }

    @Test
    @DisplayName("재인증은 이 요청만 — 세션에 저장된 SecurityContext 는 건드리지 않는다(떠나온 client 의 SSO 유지)")
    void reauthentication_leavesTheSessionContextIntact() throws Exception {
        stubClient("fan", "fan-platform");
        Authentication session = principal("ecommerce");
        SecurityContext stored = new SecurityContextImpl(session);
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, stored);

        AuthorizeSessionTenantGate gate = new AuthorizeSessionTenantGate(AUTHORIZE, registeredClientRepository);
        MockHttpServletRequest request = authorizeRequest("fan");
        request.setSession(httpSession);
        SecurityContextHolder.setContext(stored);

        AtomicReference<Authentication> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication());
        gate.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen.get()).as("this request is unauthenticated").isNull();
        assertThat(stored.getAuthentication())
                .as("the context object the session holds was replaced in the holder, not emptied")
                .isSameAs(session);
        assertThat(httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isSameAs(stored);
    }

    // -----------------------------------------------------------------------

    private void stubClient(String clientId, String tenant) {
        when(registeredClientRepository.findByClientId(clientId)).thenReturn(client(clientId, tenant));
    }

    private static RegisteredClient client(String clientId, String tenant) {
        ClientSettings.Builder settings = ClientSettings.builder().requireProofKey(true);
        if (tenant != null) {
            settings.setting(OAuthClientMapper.SETTING_TENANT_ID, tenant)
                    .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C_CONSUMER");
        }
        return RegisteredClient.withId(clientId + "-id")
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:3000/callback")
                .scope("openid")
                .clientSettings(settings.build())
                .build();
    }

    /** The principal shape both login paths build (CredentialAuthenticationProvider / SocialLoginBrowserController). */
    private static Authentication principal(String tenantId) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, tenantId);
        details.put(PrincipalDetailKeys.TENANT_TYPE, "B2C_CONSUMER");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, "acc-1");
        details.put(PrincipalDetailKeys.EMAIL, "someone@example.com");
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "someone@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        return token;
    }

    private static MockHttpServletRequest authorizeRequest(String clientId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AUTHORIZE);
        request.setServletPath(AUTHORIZE);
        if (clientId != null) {
            request.setParameter("client_id", clientId);
        }
        return request;
    }

    private Authentication runAuthorize(Authentication session, String clientId) throws Exception {
        return run(new AuthorizeSessionTenantGate(AUTHORIZE, registeredClientRepository),
                authorizeRequest(clientId), session);
    }

    /** Runs the filter and returns the authentication the next filter in the chain sees. */
    private static Authentication run(AuthorizeSessionTenantGate gate, MockHttpServletRequest request,
                                      Authentication session) throws Exception {
        SecurityContextHolder.setContext(new SecurityContextImpl(session));
        AtomicReference<Authentication> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication());
        gate.doFilter(request, new MockHttpServletResponse(), chain);
        SecurityContextHolder.clearContext();
        return seen.get();
    }
}
