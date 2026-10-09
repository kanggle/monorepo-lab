package com.example.auth.presentation;

import com.example.auth.application.AccountSecondFactorService;
import com.example.auth.application.AccountSecondFactorService.ConfirmEnrollmentResult;
import com.example.auth.application.AccountSecondFactorService.EnrollmentStatus;
import com.example.auth.application.AccountSecondFactorService.RecoveryOutcome;
import com.example.auth.application.AccountSecondFactorService.StartEnrollmentOutcome;
import com.example.auth.application.AccountSecondFactorService.StartEnrollmentResult;
import com.example.auth.application.AccountSecondFactorService.VerificationOutcome;
import com.example.auth.domain.session.PrincipalDetailKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-MONO-771 S2b — the IdP second-factor pages rendered from the REAL templates ({@code mfa-challenge},
 * {@code mfa-setup}, {@code mfa}), with a real HTTP session carrying the parked authorize and the security context
 * (auth-api.md § IdP 브라우저 화면 — 2단계 인증).
 */
@DisplayName("IdP 2단계 인증 화면 (TASK-MONO-771 S2b)")
class MfaPageSliceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000771";
    private static final String CLIENT_ID = "fan-platform-web";
    private static final String REDIRECT_URI = "http://localhost:3000/callback";

    private AccountSecondFactorService service;
    private RegisteredClientRepository clients;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        service = mock(AccountSecondFactorService.class);
        clients = mock(RegisteredClientRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new MfaPageController(service, clients, "IAM"))
                .setViewResolvers(realTemplates())
                .build();
        session = new MockHttpSession();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ /mfa/challenge

    @Test
    @DisplayName("1단계 세션 없음 → 302 /login · 서비스 호출 없음")
    void challenge_noSession() throws Exception {
        assertThat(perform(get("/mfa/challenge")).getResponse().getRedirectedUrl()).isEqualTo("/login");
        assertThat(perform(post("/mfa/challenge").param("code", "123456")).getResponse().getRedirectedUrl())
                .isEqualTo("/login");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("GET: 6자리 입력 + 복구 코드 입력(같은 폼, 버튼 1개) + 취소 · CSRF")
    void challenge_page() throws Exception {
        when(service.hasConfirmedEnrollment(ACCOUNT)).thenReturn(true);

        String html = html(perform(get("/mfa/challenge").principal(passwordSession())));

        assertThat(html).contains("name=\"code\"").contains("name=\"recoveryCode\"")
                .contains("id=\"mfa-submit\"").contains("id=\"mfa-cancel\"")
                .contains("name=\"_csrf\" value=\"test-csrf-token\"");
    }

    @Test
    @DisplayName("🔴 성공: 세션 amr = pwd+otp+mfa 로 교체 · 세션 id 회전 · 보관된 authorize 로 302")
    void challenge_success_upgradesSessionAndResumes() throws Exception {
        park();
        when(service.verifyAuthenticatorCode(ACCOUNT, "123456")).thenReturn(VerificationOutcome.ACCEPTED);
        String idBefore = session.getId();

        MvcResult result = perform(post("/mfa/challenge").param("code", "123456").principal(passwordSession()));

        assertThat(result.getResponse().getRedirectedUrl()).contains("/oauth2/authorize")
                .contains("client_id=" + CLIENT_ID);
        assertThat(sessionAmr()).containsExactly("pwd", "otp", "mfa");
        assertThat(sessionDetails()).containsEntry(PrincipalDetailKeys.ACCOUNT_ID, ACCOUNT)
                .containsEntry(PrincipalDetailKeys.TENANT_ID, "fan-platform");
        assertThat(session.getId()).as("privilege change rotates the session id").isNotEqualTo(idBefore);
    }

    @Test
    @DisplayName("🔴 F6: 소셜 세션(amr []) 도 같은 화면에서 통과 → amr = otp+mfa")
    void challenge_socialSession_upgrades() throws Exception {
        park();
        when(service.verifyAuthenticatorCode(ACCOUNT, "123456")).thenReturn(VerificationOutcome.ACCEPTED);

        perform(post("/mfa/challenge").param("code", "123456").principal(session(new ArrayList<>())));

        assertThat(sessionAmr()).containsExactly("otp", "mfa");
    }

    @Test
    @DisplayName("실패: «코드가 맞지 않습니다» · 세션 그대로 · 5회째 → 1단계 기록 삭제 + 302 /login (보관된 authorize 는 남는다)")
    void challenge_failures_fifthDropsFirstFactor() throws Exception {
        park();
        when(service.verifyAuthenticatorCode(ACCOUNT, "000000")).thenReturn(VerificationOutcome.REJECTED);
        storeContext(passwordSession());

        for (int i = 1; i <= 4; i++) {
            MvcResult r = perform(post("/mfa/challenge").param("code", "000000").principal(passwordSession()));
            assertThat(html(r)).as("attempt " + i).contains("코드가 맞지 않습니다");
        }
        MvcResult fifth = perform(post("/mfa/challenge").param("code", "000000").principal(passwordSession()));

        assertThat(fifth.getResponse().getRedirectedUrl()).isEqualTo("/login");
        SecurityContext stored = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(stored == null || stored.getAuthentication() == null)
                .as("the first-step record is gone — password/provider again").isTrue();
        assertThat(new HttpSessionRequestCache().getRequest(request(), new MockHttpServletResponse()))
                .as("the parked authorize survives so the next login resumes it").isNotNull();
    }

    @Test
    @DisplayName("복구 코드 성공 → amr = pwd+mfa (otp 아님) · 남은 코드가 많으면 바로 재개")
    void challenge_recoveryCode() throws Exception {
        park();
        when(service.verifyRecoveryCode(ACCOUNT, "ABCD-EFGH")).thenReturn(new RecoveryOutcome(true, 9));

        MvcResult r = perform(post("/mfa/challenge").param("recoveryCode", "ABCD-EFGH").principal(passwordSession()));

        assertThat(r.getResponse().getRedirectedUrl()).contains("/oauth2/authorize");
        assertThat(sessionAmr()).containsExactly("pwd", "mfa");
        verify(service, never()).verifyAuthenticatorCode(anyString(), any());
    }

    @Test
    @DisplayName("복구 코드 성공 · 남은 2개 이하 → 재발급 안내 화면 + «계속»")
    void challenge_recoveryCode_lowNotice() throws Exception {
        park();
        when(service.verifyRecoveryCode(ACCOUNT, "ABCD-EFGH")).thenReturn(new RecoveryOutcome(true, 2));

        String html = html(perform(post("/mfa/challenge").param("recoveryCode", "ABCD-EFGH")
                .principal(passwordSession())));

        assertThat(html).contains("남은 복구 코드가 <span>2</span>개").contains("id=\"mfa-continue\"")
                .contains("/oauth2/authorize");
    }

    @Test
    @DisplayName("읽기 실패 → «지금은 확인할 수 없습니다» 503 · 통과시키지 않는다")
    void challenge_unavailable_failClosed() throws Exception {
        when(service.verifyAuthenticatorCode(ACCOUNT, "123456")).thenThrow(new IllegalStateException("db"));

        MvcResult r = perform(post("/mfa/challenge").param("code", "123456").principal(passwordSession()));

        assertThat(r.getResponse().getStatus()).isEqualTo(503);
        assertThat(html(r)).contains("지금은 확인할 수 없습니다");
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test
    @DisplayName("취소 → 등록된 redirect_uri 로 error=access_denied · error_description=mfa_cancelled · state")
    void challenge_cancel() throws Exception {
        park();
        when(clients.findByClientId(CLIENT_ID)).thenReturn(client());

        MvcResult r = perform(post("/mfa/challenge").param("action", "cancel").principal(passwordSession()));

        assertThat(r.getResponse().getRedirectedUrl())
                .isEqualTo(REDIRECT_URI + "?error=access_denied&error_description=mfa_cancelled&state=s1");
    }

    // ------------------------------------------------------------------ /mfa/setup

    @Test
    @DisplayName("🔴 OD-4: 이메일 미인증 → 안내 + /email-verification 링크 · 비밀(키) 표시 없음")
    void setup_unverifiedEmail() throws Exception {
        when(service.startEnrollment(ACCOUNT, "fan-platform"))
                .thenReturn(new StartEnrollmentResult(StartEnrollmentOutcome.EMAIL_NOT_VERIFIED, null));

        MvcResult r = perform(get("/mfa/setup").principal(passwordSession()));

        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(html(r)).contains("먼저 이메일을 인증해야 합니다").contains("href=\"/email-verification\"")
                .doesNotContain("manual-key").doesNotContain("otpauth://");
    }

    @Test
    @DisplayName("GET 대기 비밀: 수동 입력 키 + otpauth URI(마스킹한 이메일 · SHA1 · 6 · 30) + 확인 입력")
    void setup_pending() throws Exception {
        when(service.startEnrollment(ACCOUNT, "fan-platform"))
                .thenReturn(new StartEnrollmentResult(StartEnrollmentOutcome.PENDING_CREATED, "GEZDGNBVGY3TQOJQ"));

        String html = html(perform(get("/mfa/setup").principal(passwordSession())));

        assertThat(html).contains("value=\"GEZD GNBV GY3T QOJQ\"")
                .contains("otpauth://totp/IAM:m***@example.com?secret=GEZDGNBVGY3TQOJQ&amp;issuer=IAM"
                        + "&amp;algorithm=SHA1&amp;digits=6&amp;period=30")
                .doesNotContain("member@example.com")
                .contains("id=\"mfa-setup-submit\"");
    }

    @Test
    @DisplayName("POST 확정 → 복구 코드 10개를 한 번만 표시 · 세션 amr += otp, mfa · «계속» = 보관된 authorize")
    void setup_confirm() throws Exception {
        park();
        List<String> codes = List.of("AAAA-BBBB", "CCCC-DDDD");
        when(service.confirmEnrollment(ACCOUNT, "123456", "member@example.com"))
                .thenReturn(new ConfirmEnrollmentResult(true, codes));

        String html = html(perform(post("/mfa/setup").param("code", "123456").principal(passwordSession())));

        assertThat(html).contains("AAAA-BBBB").contains("CCCC-DDDD").contains("id=\"mfa-continue\"")
                .contains("/oauth2/authorize");
        assertThat(sessionAmr()).containsExactly("pwd", "otp", "mfa");
    }

    @Test
    @DisplayName("POST 오답 → 같은 키로 재입력")
    void setup_wrongCode_sameKey() throws Exception {
        when(service.confirmEnrollment(ACCOUNT, "000000", "member@example.com"))
                .thenReturn(new ConfirmEnrollmentResult(false, List.of()));
        when(service.pendingManualKey(ACCOUNT)).thenReturn(Optional.of("GEZDGNBV"));

        String html = html(perform(post("/mfa/setup").param("code", "000000").principal(passwordSession())));

        assertThat(html).contains("코드가 맞지 않습니다").contains("value=\"GEZD GNBV\"");
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    // ------------------------------------------------------------------ /mfa · /mfa/recovery-codes

    @Test
    @DisplayName("GET /mfa: 세션 없음 → /login · 등록됨 + 남은 복구 코드 수")
    void status() throws Exception {
        assertThat(perform(get("/mfa")).getResponse().getRedirectedUrl()).isEqualTo("/login");
        when(service.status(ACCOUNT)).thenReturn(new EnrollmentStatus(true, 7));

        String html = html(perform(get("/mfa").principal(passwordSession())));

        assertThat(html).contains("등록되어 있습니다").contains("<span id=\"mfa-remaining\">7</span>");
    }

    @Test
    @DisplayName("재발급: mfa 없는 세션 → /mfa/challenge 먼저 · mfa 세션 → 새 코드 한 번 표시")
    void regenerate() throws Exception {
        assertThat(perform(post("/mfa/recovery-codes").principal(passwordSession())).getResponse()
                .getRedirectedUrl()).isEqualTo("/mfa/challenge");
        verify(service, never()).regenerateRecoveryCodes(anyString());

        when(service.regenerateRecoveryCodes(ACCOUNT)).thenReturn(Optional.of(List.of("WXYZ-2345")));
        String html = html(perform(post("/mfa/recovery-codes")
                .principal(session(new ArrayList<>(List.of("pwd", "otp", "mfa"))))));

        assertThat(html).contains("WXYZ-2345").contains("이전 코드는 더 이상 쓸 수 없습니다");
    }

    // ------------------------------------------------------------------ helpers

    private MvcResult perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.session(session).with(csrfAttribute())).andReturn();
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Parks an authorize request in the session's standard request cache, as the gate does. */
    private void park() {
        MockHttpServletRequest authorize = new MockHttpServletRequest("GET", "/oauth2/authorize");
        authorize.setSession(session);
        authorize.setServletPath("/oauth2/authorize");
        authorize.setQueryString("response_type=code&client_id=" + CLIENT_ID
                + "&redirect_uri=" + REDIRECT_URI + "&state=s1");
        authorize.addParameter("response_type", "code");
        authorize.addParameter("client_id", CLIENT_ID);
        authorize.addParameter("redirect_uri", REDIRECT_URI);
        authorize.addParameter("state", "s1");
        new HttpSessionRequestCache().saveRequest(authorize, new MockHttpServletResponse());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setSession(session);
        return r;
    }

    private void storeContext(Authentication auth) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sessionDetails() {
        SecurityContext ctx = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(ctx).isNotNull();
        return (Map<String, Object>) ctx.getAuthentication().getDetails();
    }

    @SuppressWarnings("unchecked")
    private List<String> sessionAmr() {
        Object amr = sessionDetails().get(PrincipalDetailKeys.AMR);
        assertThat(amr).as("allowlist: a mutable ArrayList").isInstanceOf(ArrayList.class);
        return (List<String>) amr;
    }

    private static Authentication passwordSession() {
        return session(new ArrayList<>(List.of("pwd")));
    }

    private static Authentication session(ArrayList<String> amr) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, "fan-platform");
        details.put(PrincipalDetailKeys.TENANT_TYPE, "B2C_CONSUMER");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, ACCOUNT);
        details.put(PrincipalDetailKeys.EMAIL, "member@example.com");
        details.put(PrincipalDetailKeys.AMR, amr);
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.authenticated(
                "member@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        return token;
    }

    private static RegisteredClient client() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .build();
    }

    private static RequestPostProcessor csrfAttribute() {
        return request -> {
            request.setAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
            return request;
        };
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
}
