package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.example.auth.infrastructure.security.PendingSiteConsentStore;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

    /**
     * TASK-BE-616 — CHANGED EXPECTATION. Under TASK-BE-615 this cell was
     * {@code poolSession_nonMemberConsumerSite_passes_noLoop}: the gate passed and the token endpoint
     * refused ({@code invalid_grant}). The consent screen now takes exactly that place: the chain is NOT
     * continued (no code), the request is parked, and the browser goes to {@code /consent}. Still no
     * re-authentication (which would loop) and still no token without a membership.
     */
    @Test
    @DisplayName("TASK-BE-616: 멤버십 없는 소비자 사이트 → 동의 화면으로 302 · 요청 보관 · 체인 중단(코드 없음) · 재인증 아님")
    void poolSession_nonMemberConsumerSite_redirectsToConsent() throws Exception {
        stubClient("fan", "fan-platform");
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", null, List.of()));

        Outcome out = run(principal("consumer-pool"), "fan", "GET", Map.of("state", "s-616"));

        assertThat(out.chainCalled()).as("no code is issued for this request").isFalse();
        assertThat(out.response().getStatus()).isEqualTo(302);
        assertThat(out.response().getRedirectedUrl()).isEqualTo("/consent");
        assertThat(out.request().getSession(false)).isNotNull();
        assertThat(out.request().getSession(false).getAttribute(PendingSiteConsentStore.SESSION_ATTRIBUTE))
                .as("the authorize request is parked for «accept» to resume").isNotNull();
    }

    @Test
    @DisplayName("TASK-BE-616: prompt=none · 멤버십 없음 → 화면 없이 client 로 consent_required(+state) · 요청 보관 없음")
    void poolSession_nonMember_promptNone_consentRequired() throws Exception {
        stubClient("fan", "fan-platform");
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", null, List.of()));

        Outcome out = run(principal("consumer-pool"), "fan", "GET",
                Map.of("prompt", "none", "state", "s-616", "redirect_uri", REDIRECT_URI));

        assertThat(out.chainCalled()).isFalse();
        assertThat(out.response().getRedirectedUrl())
                .startsWith(REDIRECT_URI + "?error=consent_required")
                .contains("state=s-616");
        assertThat(out.request().getSession(false)).isNull();
    }

    @Test
    @DisplayName("TASK-BE-616: prompt=none 인데 redirect_uri 가 등록값이 아님 → 리다이렉트하지 않고 그대로 통과(발급자가 거절)")
    void poolSession_nonMember_promptNone_untrustedRedirect_passes() throws Exception {
        stubClient("fan", "fan-platform");
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", null, List.of()));

        Outcome out = run(principal("consumer-pool"), "fan", "GET",
                Map.of("prompt", "none", "redirect_uri", "https://evil.example/cb"));

        assertThat(out.chainCalled()).isTrue();
        assertThat(out.response().getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("TASK-BE-616: POST authorize · 멤버십 없음 → 보관할 수 없으니 그대로 통과(발급자가 거절, 루프 없음)")
    void poolSession_nonMember_postAuthorize_passes() throws Exception {
        stubClient("fan", "fan-platform");
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", null, List.of()));
        Authentication session = principal("consumer-pool");

        Outcome out = run(session, "fan", "POST", Map.of());

        assertThat(out.chainCalled()).isTrue();
        assertThat(out.seen()).isSameAs(session);
    }

    @Test
    @DisplayName("TASK-BE-619: 본인이 떠난 멤버십(LEFT·SELF) → 동의 화면 다시 (302 /consent · 요청 보관) — 다시 동의하면 복귀")
    void poolSession_selfLeftMembership_redirectsToConsent() throws Exception {
        stubClient("fan", "fan-platform");
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", "LEFT", List.of(), "SELF"));

        Outcome out = run(principal("consumer-pool"), "fan", "GET", Map.of("state", "s-619"));

        assertThat(out.chainCalled()).as("no code is issued for this request").isFalse();
        assertThat(out.response().getStatus()).isEqualTo(302);
        assertThat(out.response().getRedirectedUrl()).isEqualTo("/consent");
        assertThat(out.request().getSession(false).getAttribute(PendingSiteConsentStore.SESSION_ATTRIBUTE))
                .isNotNull();
    }

    @Test
    @DisplayName("TASK-BE-619: 사이트 운영자가 내보낸 멤버십(LEFT·OPERATOR) → 동의 화면 아님 · 통과(발급자가 invalid_grant)")
    void poolSession_operatorLeftMembership_passes_noConsent() throws Exception {
        stubClient("fan", "fan-platform");
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", "LEFT", List.of(), "OPERATOR"));
        Authentication session = principal("consumer-pool");

        Outcome out = run(session, "fan", "GET", Map.of());

        assertThat(out.chainCalled()).isTrue();
        assertThat(out.seen()).isSameAs(session);
        assertThat(out.request().getSession(false)).isNull();
    }

    @Test
    @DisplayName("TASK-BE-621: 그 사이트 운영자가 잠근 멤버십(LOCKED) → 동의 화면 아님 · 통과(발급자가 invalid_grant) · 멤버로도 보지 않는다")
    void poolSession_siteLockedMembership_passes_noConsent() throws Exception {
        stubClient("fan", "fan-platform");
        ConsumerSiteMembershipLookupResult locked =
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", "LOCKED", List.of(), null);
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(locked);
        Authentication session = principal("consumer-pool");

        Outcome out = run(session, "fan", "GET", Map.of());

        assertThat(out.chainCalled()).isTrue();
        assertThat(out.seen()).isSameAs(session);
        assertThat(out.request().getSession(false)).as("no consent stash — consent cannot undo a site lock").isNull();
        // The issuer's own rule (TenantClaimTokenCustomizer): no ACTIVE membership → no token (invalid_grant).
        assertThat(locked.isActiveMember()).isFalse();
        assertThat(locked.isReopenableByConsent()).isFalse();
    }

    @Test
    @DisplayName("TASK-BE-616/619: 작성자 기록 없는 LEFT 멤버십(옛 account-service) → 동의 화면 아님 · 통과(발급자가 invalid_grant) — 보수 쪽")
    void poolSession_leftMembership_passes_noConsent() throws Exception {
        stubClient("fan", "fan-platform");
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", "LEFT", List.of()));
        Authentication session = principal("consumer-pool");

        Outcome out = run(session, "fan", "GET", Map.of());

        assertThat(out.chainCalled()).isTrue();
        assertThat(out.seen()).isSameAs(session);
        assertThat(out.request().getSession(false)).isNull();
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

    /**
     * TASK-MONO-772 S4 (S1-1) — the two console cells above are the bite for S1-1: the session tenant of a pool
     * principal on the console is now {@code iam} ({@link AuthorizationSessionTenant}), so a gate that compared
     * session tenant with client tenant BEFORE the BE-610 check would pass the dual-credential person through
     * (the cell above would go green → red). This cell pins the loop-free side: a failed credential lookup
     * passes, as before 772 — it never re-authenticates a pool operator into an endless login.
     */
    @Test
    @DisplayName("772 S4 (S1-1): 콘솔 client · 풀 세션 · iam 자격 조회 실패 → 통과(재인증 고리 없음) · 사이트 조회 없음")
    void poolSession_console_credentialLookupFails_passes_noLoop() throws Exception {
        stubClient("platform-console-web", "iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL)).thenThrow(new RuntimeException("db down"));
        Authentication session = principal("consumer-pool");

        assertThat(runAuthorize(session, "platform-console-web")).isSameAs(session);
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
                        .redirectUri(REDIRECT_URI).scope("openid")
                        .clientSettings(settings).build());
    }

    private static final String REDIRECT_URI = "http://localhost:3000/callback";

    /** TASK-BE-616 — what one authorize request did: response, whether the chain ran, who it saw. */
    private record Outcome(MockHttpServletRequest request, MockHttpServletResponse response,
                           boolean chainCalled, Authentication seen) {
    }

    private Outcome run(Authentication session, String clientId, String method, Map<String, String> params)
            throws Exception {
        AuthorizeSessionTenantGate gate = new AuthorizeSessionTenantGate(
                AUTHORIZE, registeredClientRepository, credentialRepository, accountServicePort);
        MockHttpServletRequest request = new MockHttpServletRequest(method, AUTHORIZE);
        request.setServletPath(AUTHORIZE);
        request.setParameter("client_id", clientId);
        request.setParameter("response_type", "code");
        params.forEach(request::setParameter);
        StringBuilder query = new StringBuilder("client_id=" + clientId + "&response_type=code");
        params.forEach((k, v) -> query.append('&').append(k).append('=').append(v));
        request.setQueryString(query.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.setContext(new SecurityContextImpl(session));
        AtomicReference<Authentication> seen = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();
        FilterChain chain = (req, res) -> {
            called.set(true);
            seen.set(SecurityContextHolder.getContext().getAuthentication());
        };
        gate.doFilter(request, response, chain);
        SecurityContextHolder.clearContext();
        return new Outcome(request, response, called.get(), seen.get());
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
