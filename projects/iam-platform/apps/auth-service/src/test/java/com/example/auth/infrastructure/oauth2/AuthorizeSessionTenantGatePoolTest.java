package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-615 — {@link AuthorizeSessionTenantGate} for a consumer-pool session
 * (multi-tenancy.md § 소비자 계정 풀 § 4): no re-authentication between consumer sites; the console
 * keeps the BE-610 rule; a B2B client re-authenticates. The per-site control (AC-2) is
 * {@link AuthorizeSessionTenantGateTest}, unchanged — plus one cell here proving the per-site path
 * never consults the pool lookup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AuthorizeSessionTenantGate — 풀 세션 (TASK-BE-615)")
class AuthorizeSessionTenantGatePoolTest {

    private static final String AUTHORIZE = "/oauth2/authorize";
    private static final String EMAIL = "pool@example.com";
    private static final String POOL_ACCOUNT = "0199de70-0000-7000-8000-0000000c0615";

    @Mock RegisteredClientRepository registeredClientRepository;
    @Mock CredentialRepository credentialRepository;
    @Mock AccountServicePort accountServicePort;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("AC-1: 풀 세션 → 멤버인 소비자 사이트 client(스토어) → 재인증 없이 통과")
    void poolSession_memberConsumerSite_passes() throws Exception {
        stubClient("store", "ecommerce");
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("ecommerce", true, "B2C_CONSUMER", "ACTIVE", List.of()));
        Authentication session = principal("consumer-pool");

        assertThat(runAuthorize(session, "store")).isSameAs(session);
    }

    @Test
    @DisplayName("멤버 아닌 소비자 사이트 → 역시 통과 (재인증하면 풀 자격으로 같은 세션 → 무한 반복) — 토큰은 발급자가 거절")
    void poolSession_nonMemberConsumerSite_passes_noLoop() throws Exception {
        stubClient("fan", "fan-platform");
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", null, List.of()));
        Authentication session = principal("consumer-pool");

        assertThat(runAuthorize(session, "fan")).isSameAs(session);
    }

    @Test
    @DisplayName("소비자 사이트가 아닌 client(wms) → 재인증 (다른 테넌트 세션과 같다)")
    void poolSession_nonConsumerClient_reauthenticates() throws Exception {
        stubClient("wms-web", "wms");
        when(accountServicePort.getConsumerSiteMembership("wms", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("wms", false, "B2B_ENTERPRISE", null, List.of()));

        assertThat(runAuthorize(principal("consumer-pool"), "wms-web")).isNull();
    }

    @Test
    @DisplayName("조회 실패 → 재인증 (보수 쪽; 폼 로그인도 같은 답이 없으면 fail-closed 라 루프 없음)")
    void poolSession_lookupFailure_reauthenticates() throws Exception {
        stubClient("store", "ecommerce");
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT))
                .thenThrow(new AccountServiceUnavailableException("down"));

        assertThat(runAuthorize(principal("consumer-pool"), "store")).isNull();
    }

    @Test
    @DisplayName("AC-3: 콘솔 client · 풀 세션 · iam 자격 없음 → BE-610 규칙 그대로 통과 (토큰은 consumer-pool 거절로 안 나온다) · 사이트 조회 없음")
    void poolSession_console_noConsoleCredential_passes_BE610() throws Exception {
        stubClient("platform-console-web", "iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL)).thenReturn(Optional.empty());
        Authentication session = principal("consumer-pool");

        assertThat(runAuthorize(session, "platform-console-web")).isSameAs(session);
        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("AC-3: 콘솔 client · 풀 세션 · 같은 이메일에 iam 자격 있음 → BE-610 재인증")
    void poolSession_console_withConsoleCredential_reauthenticates() throws Exception {
        stubClient("platform-console-web", "iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL))
                .thenReturn(Optional.of(mock(Credential.class)));

        assertThat(runAuthorize(principal("consumer-pool"), "platform-console-web")).isNull();
        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("AC-2 대조군: 묶이지 않은 사이트 계정 세션(ecommerce) → 팬 client 는 지금처럼 재인증 · 풀 조회 0")
    void siteSession_otherConsumerSite_stillReauthenticates() throws Exception {
        stubClient("fan", "fan-platform");

        assertThat(runAuthorize(principal("ecommerce"), "fan")).isNull();
        verifyNoInteractions(accountServicePort, credentialRepository);
    }

    // -----------------------------------------------------------------------

    private void stubClient(String clientId, String tenant) {
        ClientSettings settings = ClientSettings.builder().requireProofKey(true)
                .setting(OAuthClientMapper.SETTING_TENANT_ID, tenant)
                .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C_CONSUMER")
                .build();
        when(registeredClientRepository.findByClientId(clientId)).thenReturn(
                RegisteredClient.withId(clientId + "-id").clientId(clientId)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost:3000/callback").scope("openid")
                        .clientSettings(settings).build());
    }

    private static Authentication principal(String tenantId) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, tenantId);
        details.put(PrincipalDetailKeys.TENANT_TYPE, "B2C_CONSUMER");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, POOL_ACCOUNT);
        details.put(PrincipalDetailKeys.EMAIL, EMAIL);
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                EMAIL, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        return token;
    }

    private Authentication runAuthorize(Authentication session, String clientId) throws Exception {
        AuthorizeSessionTenantGate gate = new AuthorizeSessionTenantGate(
                AUTHORIZE, registeredClientRepository, credentialRepository, accountServicePort);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AUTHORIZE);
        request.setServletPath(AUTHORIZE);
        request.setParameter("client_id", clientId);
        SecurityContextHolder.setContext(new SecurityContextImpl(session));
        AtomicReference<Authentication> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication());
        gate.doFilter(request, new MockHttpServletResponse(), chain);
        SecurityContextHolder.clearContext();
        return seen.get();
    }
}
