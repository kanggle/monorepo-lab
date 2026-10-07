package com.example.auth.presentation;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.AccountServicePort.EmailVerificationConfirmOutcome;
import com.example.auth.application.port.AccountServicePort.EmailVerificationRequestOutcome;
import com.example.auth.domain.session.PrincipalDetailKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-MONO-770 — the IdP email-verification pages rendered from the REAL templates ({@code email-verification},
 * {@code verify-email}, the shared {@code fragments/auth-page}).
 *
 * <p>🔴 AC-4 (ADR-MONO-080 § 새로 생기는 위험): a failed send says «메일을 보내지 못했습니다» and offers «다시 시도» —
 * never anything that reads like «권한을 못 받음».
 */
@DisplayName("IdP 이메일 인증 화면 (TASK-MONO-770)")
class EmailVerificationPageSliceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000770";
    private static final String TOKEN = "5d2c1b0a-9999-4888-8777-666655554444";

    private AccountServicePort accountServicePort;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accountServicePort = mock(AccountServicePort.class);
        mvc = MockMvcBuilders.standaloneSetup(new EmailVerificationPageController(accountServicePort))
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

    private static Authentication poolSession() {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, "consumer-pool");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, ACCOUNT);
        details.put(PrincipalDetailKeys.EMAIL, "member@example.com");
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "member@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        return token;
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(csrfAttribute())).andReturn();
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- /email-verification

    @Test
    @DisplayName("세션 없음 → «로그인 세션이 없습니다» · 버튼 없음 · account-service 호출 없음")
    void send_noSession() throws Exception {
        MvcResult get = perform(get("/email-verification"));
        assertThat(get.getResponse().getStatus()).isEqualTo(200);
        assertThat(html(get)).contains("로그인 세션이 없습니다").doesNotContain("send-verification-email");

        MvcResult post = perform(post("/email-verification"));
        assertThat(post.getResponse().getStatus()).isEqualTo(401);
        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("세션 있음 GET → 마스킹 주소 + «인증 메일 보내기» 버튼(CSRF) · 전체 주소는 안 보인다 · 아직 아무것도 안 보낸다")
    void send_pageWithSession() throws Exception {
        String html = html(perform(get("/email-verification").principal(poolSession())));

        assertThat(html).contains("m***@example.com").doesNotContain("member@example.com");
        assertThat(html).contains("<form action=\"/email-verification\" method=\"post\"");
        assertThat(html).contains("name=\"_csrf\" value=\"test-csrf-token\"");
        assertThat(html).contains(">인증 메일 보내기</button>");
        assertThat(html).as("login/shopping/fan stay ungated — the page says so")
                .contains("로그인과 쇼핑·팬 이용은 인증과 무관하게 그대로입니다");
        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("POST → 세션의 계정·테넌트로 요청 · SENT → «인증 메일을 보냈습니다»")
    void send_sent() throws Exception {
        when(accountServicePort.requestVerificationEmail(ACCOUNT, "consumer-pool"))
                .thenReturn(EmailVerificationRequestOutcome.SENT);

        MvcResult result = perform(post("/email-verification").principal(poolSession()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(html(result)).contains("인증 메일을 보냈습니다");
        verify(accountServicePort).requestVerificationEmail(ACCOUNT, "consumer-pool");
    }

    @Test
    @DisplayName("🔴 AC-4: 발송 실패 → «메일을 보내지 못했습니다 · 잠시 뒤 다시» + «다시 시도» 버튼 — «권한» 이라는 말은 없다")
    void send_failed_saysCouldNotSend_offersRetry() throws Exception {
        when(accountServicePort.requestVerificationEmail(ACCOUNT, "consumer-pool"))
                .thenReturn(EmailVerificationRequestOutcome.SEND_FAILED);

        MvcResult result = perform(post("/email-verification").principal(poolSession()));

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        String html = html(result);
        assertThat(html).contains("메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
        assertThat(html).contains(">다시 시도</button>");
        // The message element only — the template's own comment NAMES the forbidden reading (it says «권한을 못
        // 받음» to forbid it), so a page-wide doesNotContain would bite on the documentation, not the screen.
        Matcher message = Pattern.compile("id=\"verification-result\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html);
        assertThat(message.find()).as("the failure message element is rendered").isTrue();
        assertThat(message.group(1))
                .contains("메일을 보내지 못했습니다")
                .as("the failure must not read as «you were not given the role»")
                .doesNotContain("권한");
    }

    @Test
    @DisplayName("영구 실패 · 이미 인증 · 재발송 제한 · 대상 아님 → 각자의 문구, 재시도 버튼 없음")
    void send_otherOutcomes() throws Exception {
        when(accountServicePort.requestVerificationEmail(ACCOUNT, "consumer-pool"))
                .thenReturn(EmailVerificationRequestOutcome.UNDELIVERABLE,
                        EmailVerificationRequestOutcome.ALREADY_VERIFIED,
                        EmailVerificationRequestOutcome.RATE_LIMITED,
                        EmailVerificationRequestOutcome.NOT_APPLICABLE);

        String undeliverable = html(perform(post("/email-verification").principal(poolSession())));
        assertThat(undeliverable).contains("이 주소로는 메일을 보낼 수 없습니다").doesNotContain("<button");
        assertThat(html(perform(post("/email-verification").principal(poolSession()))))
                .contains("이미 인증된 이메일입니다").doesNotContain("<button");
        assertThat(html(perform(post("/email-verification").principal(poolSession()))))
                .contains("5분 뒤에 다시").doesNotContain("<button");
        assertThat(html(perform(post("/email-verification").principal(poolSession()))))
                .contains("이메일 인증 대상이 아닙니다").doesNotContain("<button");
    }

    // ---------------------------------------------------------------- /verify-email

    @Test
    @DisplayName("🔴 링크 GET 은 아무것도 바꾸지 않는다(메일 스캐너 선조회) — 버튼만 · 토큰은 hidden 필드에만")
    void confirmPage_getIsSideEffectFree() throws Exception {
        MvcResult result = perform(get("/verify-email").param("token", TOKEN));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String html = html(result);
        assertThat(html).contains("<form action=\"/verify-email\" method=\"post\"");
        assertThat(html).contains("type=\"hidden\" name=\"token\" value=\"" + TOKEN + "\"");
        assertThat(html.split(TOKEN, -1).length - 1).as("R4: the token appears once — the hidden field").isEqualTo(1);
        assertThat(html).contains(">이메일 인증 완료</button>");
        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("POST → account-service 확인 · VERIFIED → «이메일이 인증되었습니다» · 세션 없이도 된다(다른 기기)")
    void confirm_verified_noSessionNeeded() throws Exception {
        when(accountServicePort.confirmEmailVerification(TOKEN)).thenReturn(EmailVerificationConfirmOutcome.VERIFIED);

        MvcResult result = perform(post("/verify-email").param("token", TOKEN));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(html(result)).contains("이메일이 인증되었습니다").doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("만료·사용됨 → 안내 + «인증 메일 다시 받기» 링크 · 확인 불가 → 같은 버튼을 다시 준다(토큰 미소비)")
    void confirm_invalid_and_unavailable() throws Exception {
        when(accountServicePort.confirmEmailVerification(TOKEN)).thenReturn(
                EmailVerificationConfirmOutcome.INVALID_OR_EXPIRED, EmailVerificationConfirmOutcome.UNAVAILABLE);

        MvcResult invalid = perform(post("/verify-email").param("token", TOKEN));
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(html(invalid)).contains("링크가 만료되었거나 이미 사용되었습니다")
                .contains("href=\"/email-verification\"").doesNotContain("<form");

        MvcResult unavailable = perform(post("/verify-email").param("token", TOKEN));
        assertThat(unavailable.getResponse().getStatus()).isEqualTo(503);
        assertThat(html(unavailable)).contains("같은 링크로 다시 됩니다").contains(">이메일 인증 완료</button>");
    }

    @Test
    @DisplayName("토큰 없는 링크 → 만료 안내 · 호출 없음")
    void confirm_missingToken() throws Exception {
        assertThat(perform(get("/verify-email")).getResponse().getStatus()).isEqualTo(400);
        assertThat(perform(post("/verify-email")).getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(accountServicePort);
    }
}
