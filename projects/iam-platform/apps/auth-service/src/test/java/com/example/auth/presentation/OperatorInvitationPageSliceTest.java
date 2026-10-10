package com.example.auth.presentation;

import com.example.auth.application.port.ConsumerPoolSignupPort;
import com.example.auth.application.port.OperatorInvitationAcceptancePort;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.AcceptOutcome;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.AcceptResult;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.Preview;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.PreviewOutcome;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.PreviewResult;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.infrastructure.security.OperatorInvitationContinuation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.savedrequest.SavedRequest;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-MONO-772 S3 — the IdP operator-invitation pages rendered from the REAL templates
 * ({@code operator-invitation-accept}, {@code operator-invitation-signup}, {@code fragments/auth-page}).
 *
 * <ul>
 *   <li>🔴 GET changes nothing (no accept call — mail scanners fetch first);</li>
 *   <li>🔴 POST sends the <b>session</b> principal's account id — a form field named {@code accountId} is ignored;</li>
 *   <li>a non-pool session never reaches admin-service;</li>
 *   <li>every admin answer has its screen (auth-api.md § 운영자 초대 수락 POST table);</li>
 *   <li>R4: the token appears only in the hidden field / the signup link, the address only masked;</li>
 *   <li>the site-less signup parks the acceptance page as the login continuation (S1-7's trigger).</li>
 * </ul>
 */
@DisplayName("IdP 운영자 초대 수락 · 사이트 없는 풀 가입 화면 (TASK-MONO-772 S3)")
class OperatorInvitationPageSliceTest {

    private static final String TOKEN = "Zq3-raw_invitation_token_base64url_772s3";
    private static final String POOL_ACCOUNT = "0199de70-0000-7000-8000-0000000acc01";
    private static final String CONSOLE = "https://console.example.test";

    private OperatorInvitationAcceptancePort acceptancePort;
    private ConsumerPoolSignupPort signupPort;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        acceptancePort = mock(OperatorInvitationAcceptancePort.class);
        signupPort = mock(ConsumerPoolSignupPort.class);
        mvc = MockMvcBuilders.standaloneSetup(new OperatorInvitationPageController(acceptancePort, signupPort, CONSOLE))
                .setViewResolvers(realTemplates())
                .build();
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

    private static Authentication session(String tenantId, String accountId) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, tenantId);
        details.put(PrincipalDetailKeys.ACCOUNT_ID, accountId);
        details.put(PrincipalDetailKeys.EMAIL, "invitee@example.com");
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "invitee@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        return token;
    }

    private static Authentication poolSession() {
        return session("consumer-pool", POOL_ACCOUNT);
    }

    private static PreviewResult found(String status, boolean expired) {
        return new PreviewResult(PreviewOutcome.FOUND, new Preview("acme-corp", "Acme Corp", "i*****@example.com",
                List.of("SUPPORT_LOCK"), status, expired, Instant.parse("2026-10-17T10:00:00Z")));
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(csrfAttribute())).andReturn();
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The text of the result element — the template's comments name things the screen must not say. */
    private static String message(String html) {
        Matcher m = Pattern.compile("id=\"invitation-result\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html);
        assertThat(m.find()).as("the result element is rendered").isTrue();
        return m.group(1);
    }

    private static int occurrences(String html, String needle) {
        return html.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    // ── GET ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /operator-invitations/accept — 아무것도 바꾸지 않는다")
    class GetPage {

        @Test
        @DisplayName("세션 없음 → «{회사}의 운영자로 초대 · {마스킹}» + 로그인 · IAM 계정 만들기 · 수락 호출 없음 · 수락 화면이 로그인 계속 지점으로 저장된다")
        void noSession_loginRequired_parksContinuation() throws Exception {
            when(acceptancePort.preview(TOKEN)).thenReturn(found("PENDING", false));

            MvcResult result = perform(get("/operator-invitations/accept").param("token", TOKEN));

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            String html = html(result);
            assertThat(message(html)).contains("Acme Corp").contains("i*****@example.com").contains("개인(IAM) 계정");
            assertThat(html).contains("id=\"invitation-login\"").contains("href=\"/login\"");
            assertThat(html).contains("id=\"invitation-signup\"")
                    .contains("/operator-invitations/signup?token=" + TOKEN);
            assertThat(html).doesNotContain("invitee@example.com").doesNotContain("id=\"accept-invitation\"");
            assertThat(occurrences(html, TOKEN)).as("R4: only the signup link carries the token").isEqualTo(1);

            SavedRequest saved = (SavedRequest) result.getRequest().getSession()
                    .getAttribute("SPRING_SECURITY_SAVED_REQUEST");
            assertThat(saved).as("the login comes back here").isNotNull();
            assertThat(saved.getRedirectUrl()).isEqualTo("/operator-invitations/accept?token=" + TOKEN);
            assertThat(OperatorInvitationContinuation.isAcceptanceContinuation(saved)).isTrue();
            verify(acceptancePort, never()).accept(anyString(), anyString());
        }

        @Test
        @DisplayName("풀 세션 → 회사 · 역할 · «이 계정으로 수락» + 수락 버튼(CSRF · 토큰은 hidden 하나) · 아직 수락 안 함")
        void poolSession_confirmForm() throws Exception {
            when(acceptancePort.preview(TOKEN)).thenReturn(found("PENDING", false));

            String html = html(perform(get("/operator-invitations/accept").param("token", TOKEN)
                    .principal(poolSession())));

            assertThat(message(html)).contains("Acme Corp").contains("이 계정으로 수락합니다");
            assertThat(html).contains("SUPPORT_LOCK");
            assertThat(html).contains("<form action=\"/operator-invitations/accept\" method=\"post\"");
            assertThat(html).contains("name=\"_csrf\" value=\"test-csrf-token\"");
            assertThat(html).contains("type=\"hidden\" name=\"token\" value=\"" + TOKEN + "\"");
            assertThat(html).doesNotContain("name=\"accountId\"");
            assertThat(occurrences(html, TOKEN)).isEqualTo(1);
            verify(acceptancePort, never()).accept(anyString(), anyString());
        }

        @Test
        @DisplayName("풀이 아닌 세션(사이트 계정 · iam 자격) → «개인(IAM) 계정으로만» + 로그아웃 · 수락 폼 없음")
        void nonPoolSession_refusedWithoutCallingAdmin() throws Exception {
            when(acceptancePort.preview(TOKEN)).thenReturn(found("PENDING", false));

            for (Authentication s : List.of(session("ecommerce", "site-acc"), session("iam", "iam-acc"))) {
                String html = html(perform(get("/operator-invitations/accept").param("token", TOKEN).principal(s)));
                assertThat(message(html)).contains("개인(IAM) 계정으로만 수락할 수 있습니다");
                assertThat(html).contains("id=\"invitation-logout\"").doesNotContain("id=\"accept-invitation\"");
                assertThat(html).doesNotContain(TOKEN);
            }
            verify(acceptancePort, never()).accept(anyString(), anyString());
        }

        @Test
        @DisplayName("미리보기 404 → 찾을 수 없음 · 만료 → 410 · 이미 수락 → 콘솔 링크 · 미리보기 실패 → 503")
        void previewStates() throws Exception {
            when(acceptancePort.preview(TOKEN)).thenReturn(PreviewResult.notFound(), found("PENDING", true),
                    found("ACCEPTED", false), PreviewResult.unavailable());

            MvcResult notFound = perform(get("/operator-invitations/accept").param("token", TOKEN));
            assertThat(notFound.getResponse().getStatus()).isEqualTo(404);
            assertThat(message(html(notFound))).contains("초대를 찾을 수 없습니다");

            MvcResult expired = perform(get("/operator-invitations/accept").param("token", TOKEN).principal(poolSession()));
            assertThat(expired.getResponse().getStatus()).isEqualTo(410);
            assertThat(message(html(expired))).contains("초대가 만료되었습니다");

            String accepted = html(perform(get("/operator-invitations/accept").param("token", TOKEN)));
            assertThat(message(accepted)).contains("이미 수락된 초대입니다");
            assertThat(accepted).contains("href=\"" + CONSOLE + "\"");

            MvcResult down = perform(get("/operator-invitations/accept").param("token", TOKEN));
            assertThat(down.getResponse().getStatus()).isEqualTo(503);
            assertThat(message(html(down))).contains("지금은 초대를 확인할 수 없습니다");
            verify(acceptancePort, never()).accept(anyString(), anyString());
        }

        @Test
        @DisplayName("토큰 없음 → 404 · 아무것도 묻지 않는다")
        void noToken() throws Exception {
            MvcResult result = perform(get("/operator-invitations/accept"));
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            verifyNoInteractions(acceptancePort);
        }
    }

    // ── POST ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /operator-invitations/accept")
    class PostAccept {

        @Test
        @DisplayName("🔴 accountId 는 세션에서 — 폼의 accountId 는 무시된다 · 200 → «운영자가 되었습니다» + 콘솔로 가기")
        void accountIdFromSession_neverTheForm() throws Exception {
            when(acceptancePort.accept(TOKEN, POOL_ACCOUNT)).thenReturn(new AcceptResult(AcceptOutcome.ACCEPTED, "acme-corp"));
            when(acceptancePort.preview(TOKEN)).thenReturn(found("ACCEPTED", false));

            MvcResult result = perform(post("/operator-invitations/accept")
                    .param("token", TOKEN).param("accountId", "someone-elses-account")
                    .principal(poolSession()));

            verify(acceptancePort).accept(TOKEN, POOL_ACCOUNT);
            verify(acceptancePort, never()).accept(TOKEN, "someone-elses-account");
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            String html = html(result);
            assertThat(message(html)).contains("Acme Corp").contains("운영자가 되었습니다");
            assertThat(html).contains("id=\"invitation-console\"").contains("href=\"" + CONSOLE + "\"");
            assertThat(html).doesNotContain(TOKEN);
        }

        @Test
        @DisplayName("풀이 아닌 세션 · 세션 없음 → admin 을 부르지 않는다 (403 / 401 + 로그인)")
        void nonPoolOrNoSession_neverCallsAdmin() throws Exception {
            when(acceptancePort.preview(TOKEN)).thenReturn(found("PENDING", false));

            MvcResult site = perform(post("/operator-invitations/accept").param("token", TOKEN)
                    .principal(session("ecommerce", "site-acc")));
            assertThat(site.getResponse().getStatus()).isEqualTo(403);
            assertThat(message(html(site))).contains("개인(IAM) 계정으로만");

            MvcResult none = perform(post("/operator-invitations/accept").param("token", TOKEN)
                    .session(new MockHttpSession()));
            assertThat(none.getResponse().getStatus()).isEqualTo(401);
            assertThat(html(none)).contains("id=\"invitation-login\"");

            verify(acceptancePort, never()).accept(anyString(), anyString());
        }

        @Test
        @DisplayName("admin 의 답마다 화면 — 미인증은 /email-verification 링크 · 장애는 다시 시도 버튼(토큰 hidden)")
        void everyAnswerHasItsScreen() throws Exception {
            when(acceptancePort.preview(TOKEN)).thenReturn(found("PENDING", false));
            Object[][] cases = {
                    {AcceptOutcome.ALREADY_ACCEPTED, 200, "이미 수락했습니다"},
                    {AcceptOutcome.EMAIL_NOT_VERIFIED, 403, "먼저 이메일을 인증해야 합니다"},
                    {AcceptOutcome.EMAIL_MISMATCH, 403, "지금 계정의 주소가 다릅니다"},
                    {AcceptOutcome.ACCOUNT_NOT_ELIGIBLE, 403, "개인(IAM) 계정으로만"},
                    {AcceptOutcome.NOT_FOUND, 404, "초대를 찾을 수 없습니다"},
                    {AcceptOutcome.ALREADY_USED, 409, "이미 다른 계정으로 수락된 초대입니다"},
                    {AcceptOutcome.ALREADY_PROVISIONED, 409, "한 계정이 한 회사의 운영자만"},
                    {AcceptOutcome.EMAIL_CONFLICT, 409, "같은 주소의 운영자가 이미 있습니다"},
                    {AcceptOutcome.INVALIDATED, 409, "더 이상 유효하지 않습니다"},
                    {AcceptOutcome.EXPIRED, 410, "초대가 만료되었습니다"},
                    {AcceptOutcome.UNAVAILABLE, 503, "지금은 수락할 수 없습니다"},
            };
            for (Object[] c : cases) {
                when(acceptancePort.accept(TOKEN, POOL_ACCOUNT)).thenReturn(new AcceptResult((AcceptOutcome) c[0], null));
                MvcResult result = perform(post("/operator-invitations/accept").param("token", TOKEN)
                        .principal(poolSession()));
                assertThat(result.getResponse().getStatus()).as("%s", c[0]).isEqualTo((int) c[1]);
                String html = html(result);
                assertThat(message(html)).as("%s", c[0]).contains((String) c[2]);
                if (c[0] == AcceptOutcome.EMAIL_NOT_VERIFIED) {
                    assertThat(html).contains("href=\"/email-verification\"");
                }
                if (c[0] == AcceptOutcome.UNAVAILABLE) {
                    assertThat(html).contains(">다시 시도</button>")
                            .contains("type=\"hidden\" name=\"token\" value=\"" + TOKEN + "\"");
                } else {
                    assertThat(html).as("%s — no token on a final screen", c[0]).doesNotContain(TOKEN);
                }
            }
        }
    }

    // ── signup ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("/operator-invitations/signup — 사이트 없는 풀 가입")
    class Signup {

        @Test
        @DisplayName("GET → 가입 폼(토큰 hidden) · «스토어 · 팬 사이트에는 가입되지 않습니다» · 로그인 링크도 수락 화면으로 돌아오게 저장")
        void form() throws Exception {
            MvcResult result = perform(get("/operator-invitations/signup").param("token", TOKEN));
            String html = html(result);
            assertThat(html).contains("<form action=\"/operator-invitations/signup\" method=\"post\"");
            assertThat(html).contains("type=\"hidden\" name=\"token\" value=\"" + TOKEN + "\"");
            assertThat(html).contains("스토어 · 팬 사이트에는 가입되지 않습니다");
            assertThat(occurrences(html, TOKEN)).isEqualTo(1);
            SavedRequest saved = (SavedRequest) result.getRequest().getSession()
                    .getAttribute("SPRING_SECURITY_SAVED_REQUEST");
            assertThat(OperatorInvitationContinuation.isAcceptanceContinuation(saved)).isTrue();
        }

        @Test
        @DisplayName("201 → /login?registered · 로그인 뒤 수락 화면으로(저장된 요청) · 풀 가입 포트에 입력값 그대로")
        void created_redirectsToLogin_andParksAcceptance() throws Exception {
            when(signupPort.signup("invitee@example.com", "Password1!", "피초대자"))
                    .thenReturn(ConsumerPoolSignupPort.Outcome.CREATED);

            MvcResult result = perform(post("/operator-invitations/signup").param("token", TOKEN)
                    .param("email", " invitee@example.com ").param("displayName", "피초대자")
                    .param("password", "Password1!").param("confirmPassword", "Password1!"));

            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?registered");
            SavedRequest saved = (SavedRequest) result.getRequest().getSession()
                    .getAttribute("SPRING_SECURITY_SAVED_REQUEST");
            assertThat(saved.getRedirectUrl()).isEqualTo("/operator-invitations/accept?token=" + TOKEN);
        }

        @Test
        @DisplayName("이미 있는 주소(풀 · 스토어/팬) → 409 한 문구 · 형식 · 불일치 → 포트 호출 없음 · 장애 → 503")
        void refusals() throws Exception {
            when(signupPort.signup("invitee@example.com", "Password1!", ""))
                    .thenReturn(ConsumerPoolSignupPort.Outcome.ALREADY_EXISTS, ConsumerPoolSignupPort.Outcome.UNAVAILABLE);

            MvcResult exists = perform(post("/operator-invitations/signup").param("token", TOKEN)
                    .param("email", "invitee@example.com").param("password", "Password1!")
                    .param("confirmPassword", "Password1!"));
            assertThat(exists.getResponse().getStatus()).isEqualTo(409);
            assertThat(html(exists)).contains("이미 사용 중인 주소입니다");

            MvcResult down = perform(post("/operator-invitations/signup").param("token", TOKEN)
                    .param("email", "invitee@example.com").param("password", "Password1!")
                    .param("confirmPassword", "Password1!"));
            assertThat(down.getResponse().getStatus()).isEqualTo(503);

            assertThat(html(perform(post("/operator-invitations/signup").param("token", TOKEN)
                    .param("email", "not-an-email").param("password", "Password1!")
                    .param("confirmPassword", "Password1!")))).contains("이메일 형식이 올바르지 않습니다");
            assertThat(html(perform(post("/operator-invitations/signup").param("token", TOKEN)
                    .param("email", "a@example.com").param("password", "Password1!")
                    .param("confirmPassword", "Password2!")))).contains("비밀번호가 일치하지 않습니다");
            verify(signupPort, org.mockito.Mockito.times(2)).signup(anyString(), anyString(), anyString());
        }
    }
}
