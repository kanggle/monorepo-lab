package com.example.auth.presentation;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.example.auth.infrastructure.security.PendingSiteConsentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-BE-616 — the first-visit consent page, rendered from the REAL {@code consent.html} (and the
 * shared {@code fragments/auth-page.html}) and driven through the real {@link PendingSiteConsentStore}.
 *
 * <p>Cells: the page wears the parked client's brand and is keyboard operable (AC-5); «accept» writes
 * through account-service and resumes the parked authorize (AC-1); «decline» answers the client
 * {@code access_denied} with its {@code state} and writes nothing (AC-1); the parked request survives a
 * reload / back (Edge Case 1); a non-pool session and a direct visit get the «expired» page.
 */
class SiteConsentPageSliceTest {

    private static final String POOL_ACCOUNT = "0199de70-0000-7000-8000-0000000d0616";
    private static final String REDIRECT_URI = "http://localhost:3000/callback";
    private static final String AUTHORIZE_QUERY = "response_type=code&client_id=fan&redirect_uri="
            + REDIRECT_URI + "&scope=openid&state=st-616&code_challenge=abc&code_challenge_method=S256";

    private RegisteredClientRepository clients;
    private AccountServicePort accountServicePort;
    private PendingSiteConsentStore store;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        clients = mock(RegisteredClientRepository.class);
        accountServicePort = mock(AccountServicePort.class);
        store = new PendingSiteConsentStore(clients);
        mvc = MockMvcBuilders.standaloneSetup(new SiteConsentPageController(store, accountServicePort))
                .setViewResolvers(realTemplates())
                .build();
        ClientSettings settings = ClientSettings.builder().requireProofKey(true)
                .setting(OAuthClientMapper.SETTING_TENANT_ID, "fan-platform")
                .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C_CONSUMER")
                .setting(OAuthClientMapper.SETTING_BRANDING_SERVICE_NAME, "IAM")
                .setting(OAuthClientMapper.SETTING_BRANDING_DESCRIPTION, "IAM으로 안전하게 로그인합니다")
                .setting(OAuthClientMapper.SETTING_BRANDING_PRIMARY_COLOR, "#9333ea")
                .build();
        when(clients.findByClientId("fan")).thenReturn(RegisteredClient.withId("fan-id").clientId("fan")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI).scope("openid")
                .clientSettings(settings).build());
    }

    private static ThymeleafViewResolver realTemplates() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver);
        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return viewResolver;
    }

    private static RequestPostProcessor csrfAttribute() {
        return request -> {
            request.setAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
            return request;
        };
    }

    /** What the gate does: park the authorize request in the session. */
    private MockHttpSession parkedAuthorize() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest authorize = new MockHttpServletRequest("GET", "/oauth2/authorize");
        authorize.setServerName("localhost");
        authorize.setServerPort(8081);
        authorize.setSession(session);
        authorize.setQueryString(AUTHORIZE_QUERY);
        for (String pair : AUTHORIZE_QUERY.split("&")) {
            String[] kv = pair.split("=", 2);
            authorize.setParameter(kv[0], kv[1]);
        }
        store.save(authorize, new MockHttpServletResponse());
        return session;
    }

    private static Authentication principal(String tenantId) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, tenantId);
        details.put(PrincipalDetailKeys.TENANT_TYPE, "B2C_CONSUMER");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, POOL_ACCOUNT);
        details.put(PrincipalDetailKeys.EMAIL, "pool@example.com");
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "pool@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        return token;
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(csrfAttribute())).andReturn();
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static int count(String html, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(html);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    @DisplayName("AC-5: 동의 화면 — 사이트 색·설명, 한 폼에 버튼 둘(둘 다 type=submit, 네이티브 버튼 → 키보드 조작), CSRF, 360px 카드")
    void page_brandedAndKeyboardOperable() throws Exception {
        MvcResult result = perform(get("/consent").session(parkedAuthorize()).principal(principal("consumer-pool")));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String html = html(result);
        assertThat(html).contains("<title>이 사이트 이용 동의</title>");
        assertThat(html).contains("<h1>이 사이트 이용 동의</h1>");
        // TASK-BE-619 (owner decision «동의 화면 전용 부제»): the consent's own subtitle, never the login one.
        assertThat(html).contains("이 사이트에서 내 IAM 계정을 쓰도록 허용합니다.");
        assertThat(html).doesNotContain("IAM으로 안전하게 로그인합니다");
        assertThat(html).contains("--brand: #9333ea");
        assertThat(html).contains("<html lang=\"ko\"");
        assertThat(count(html, "<form\\b")).isEqualTo(1);
        assertThat(html).contains("<form action=\"/consent\" method=\"post\"");
        assertThat(html).contains("name=\"_csrf\" value=\"test-csrf-token\"");
        assertThat(html).contains("<button type=\"submit\" name=\"decision\" value=\"accept\" class=\"primary\" autofocus>동의하고 계속</button>");
        assertThat(html).contains("<button type=\"submit\" name=\"decision\" value=\"decline\" class=\"secondary\">동의하지 않음</button>");
        // Only the <form> tag: the enhance script and a template comment both NAME the attribute.
        Matcher formTag = Pattern.compile("<form\\b[^>]*>").matcher(html);
        assertThat(formTag.find()).isTrue();
        assertThat(formTag.group())
                .as("no busy-label: the enhance script would disable «accept» and drop its value")
                .doesNotContain("data-busy-label");
        assertThat(html).as("narrow screens: the card never exceeds the viewport (400px)")
                .contains("max-width: calc(100vw - 32px)");
        // TASK-BE-619: the only call is the site-name read (unstubbed → empty → «이 사이트»); nothing is written.
        verify(accountServicePort).getTenant("fan-platform");
        verifyNoMoreInteractions(accountServicePort);
    }

    @Test
    @DisplayName("TASK-BE-619: 동의 화면 부제 = «{사이트 이름}에서 내 IAM 계정을 쓰도록 허용합니다.» — 이름은 보관된 client 의 사이트 테넌트 display_name · 조회 실패는 «이 사이트»")
    void subtitle_namesTheSite_failSoft() throws Exception {
        when(accountServicePort.getTenant("fan-platform")).thenReturn(Optional.of(
                new AccountServicePort.TenantLookupResult("B2C_CONSUMER", "ACTIVE", "Fan Platform")));

        String named = html(perform(get("/consent").session(parkedAuthorize()).principal(principal("consumer-pool"))));
        assertThat(named).contains("Fan Platform에서 내 IAM 계정을 쓰도록 허용합니다.");
        assertThat(named).doesNotContain("로그인합니다");
        assertThat(named).as("a returning (self-left) member reads it too — no «처음»").doesNotContain("처음 이용");

        when(accountServicePort.getTenant("fan-platform")).thenThrow(new RuntimeException("down"));
        MvcResult failed = perform(get("/consent").session(parkedAuthorize()).principal(principal("consumer-pool")));
        assertThat(failed.getResponse().getStatus()).as("the page never fails over a label").isEqualTo(200);
        assertThat(html(failed)).contains("이 사이트에서 내 IAM 계정을 쓰도록 허용합니다.");
    }

    @Test
    @DisplayName("AC-1: 동의 → account-service 동의 쓰기 → 보관된 authorize 로 302 · 보관 요청 제거")
    void accept_writesMembership_andResumesAuthorize() throws Exception {
        when(accountServicePort.consentToConsumerSite("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", "ACTIVE", List.of()));
        MockHttpSession session = parkedAuthorize();

        MvcResult result = perform(post("/consent").param("decision", "accept")
                .session(session).principal(principal("consumer-pool")));

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo("http://localhost:8081/oauth2/authorize?" + AUTHORIZE_QUERY);
        verify(accountServicePort).consentToConsumerSite("fan-platform", POOL_ACCOUNT);
        assertThat(session.getAttribute(PendingSiteConsentStore.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("AC-1: 거절 → client 로 error=access_denied + state · 멤버십 쓰기 없음 · 보관 요청 제거 (루프 없음)")
    void decline_returnsAccessDeniedToClient_noWrite() throws Exception {
        MockHttpSession session = parkedAuthorize();

        MvcResult result = perform(post("/consent").param("decision", "decline")
                .session(session).principal(principal("consumer-pool")));

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith(REDIRECT_URI + "?error=access_denied&error_description=")
                .endsWith("&state=st-616")
                .doesNotContain("code=");
        verifyNoInteractions(accountServicePort);
        assertThat(session.getAttribute(PendingSiteConsentStore.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("Edge Case 1: 새로고침/뒤로가기 → 같은 보관 요청으로 화면이 다시 그려진다(GET 은 보관 요청을 지우지 않는다)")
    void reload_keepsParkedRequest() throws Exception {
        MockHttpSession session = parkedAuthorize();

        perform(get("/consent").session(session).principal(principal("consumer-pool")));
        MvcResult again = perform(get("/consent").session(session).principal(principal("consumer-pool")));

        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(html(again)).contains("value=\"accept\"");
        assertThat(session.getAttribute(PendingSiteConsentStore.SESSION_ATTRIBUTE)).isNotNull();
    }

    @Test
    @DisplayName("account-service 장애 → 503 · 다시 누를 수 있게 보관 요청 유지 · role=alert 오류")
    void accept_accountServiceDown_keepsParkedRequest() throws Exception {
        when(accountServicePort.consentToConsumerSite("fan-platform", POOL_ACCOUNT))
                .thenThrow(new AccountServiceUnavailableException("down"));
        MockHttpSession session = parkedAuthorize();

        MvcResult result = perform(post("/consent").param("decision", "accept")
                .session(session).principal(principal("consumer-pool")));

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(html(result)).contains("role=\"alert\"").contains("잠시 후 다시 시도해 주세요");
        assertThat(html(result)).contains("value=\"accept\"");
        assertThat(session.getAttribute(PendingSiteConsentStore.SESSION_ATTRIBUTE)).isNotNull();
    }

    @Test
    @DisplayName("동의 후에도 ACTIVE 가 아님(정지 사이트 등) → 토큰 없이 client 로 access_denied")
    void accept_butNotActive_accessDenied() throws Exception {
        when(accountServicePort.consentToConsumerSite("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", null, List.of()));

        MvcResult result = perform(post("/consent").param("decision", "accept")
                .session(parkedAuthorize()).principal(principal("consumer-pool")));

        assertThat(result.getResponse().getRedirectedUrl()).startsWith(REDIRECT_URI + "?error=access_denied");
    }

    @Test
    @DisplayName("풀 세션이 아님(사이트별 계정) → «만료» 화면 400 · 버튼 없음 · 쓰기 없음")
    void nonPoolSession_expired() throws Exception {
        MvcResult result = perform(post("/consent").param("decision", "accept")
                .session(parkedAuthorize()).principal(principal("ecommerce")));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(html(result)).contains("만료되었습니다").doesNotContain("value=\"accept\"");
        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("보관된 요청 없이 직접 방문 → «만료» 화면 400 · 기본 브랜딩")
    void directVisit_expired() throws Exception {
        MvcResult result = perform(get("/consent").principal(principal("consumer-pool")));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(html(result)).contains("만료되었습니다").doesNotContain("<form");
    }

    @Test
    @DisplayName("decision 이 없거나 이상함 → 400 · 화면 다시 · 쓰기 없음")
    void unknownDecision_400() throws Exception {
        MvcResult result = perform(post("/consent").param("decision", "maybe")
                .session(parkedAuthorize()).principal(principal("consumer-pool")));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(html(result)).contains("value=\"accept\"");
        // TASK-BE-619: the re-rendered page reads the site name (subtitle); no write.
        verify(accountServicePort).getTenant("fan-platform");
        verifyNoMoreInteractions(accountServicePort);
    }

    @Test
    @DisplayName("거절 · redirect_uri 를 신뢰할 수 없음(등록값 아님) → 리다이렉트하지 않고 IAM 화면에 «동의하지 않았습니다»")
    void decline_untrustedRedirect_staysOnPage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest authorize = new MockHttpServletRequest("GET", "/oauth2/authorize");
        authorize.setSession(session);
        authorize.setQueryString("client_id=fan&redirect_uri=https://evil.example/cb");
        authorize.setParameter("client_id", "fan");
        authorize.setParameter("redirect_uri", "https://evil.example/cb");
        store.save(authorize, new MockHttpServletResponse());

        MvcResult result = perform(post("/consent").param("decision", "decline")
                .session(session).principal(principal("consumer-pool")));

        assertThat(result.getResponse().getRedirectedUrl()).isNull();
        assertThat(html(result)).contains("동의하지 않았습니다").doesNotContain("value=\"accept\"");
    }
}
