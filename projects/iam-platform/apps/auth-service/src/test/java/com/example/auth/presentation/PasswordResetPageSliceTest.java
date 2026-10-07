package com.example.auth.presentation;

import com.example.auth.application.ConfirmPasswordResetUseCase;
import com.example.auth.application.RequestPasswordResetUseCase;
import com.example.auth.application.command.ConfirmPasswordResetCommand;
import com.example.auth.application.command.RequestPasswordResetCommand;
import com.example.auth.application.exception.PasswordResetTokenInvalidException;
import com.example.auth.domain.credentials.PasswordPolicyViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-BE-627 (TASK-MONO-770 의 남은 화면) — the IdP password-reset pages rendered from the REAL
 * templates ({@code password-reset-request}, {@code password-reset}, the shared
 * {@code fragments/auth-page}). Security-chain behaviour (CSRF rejection, anonymous-GET permitAll)
 * is covered separately in {@link PasswordResetPageSecurityChainSliceTest}.
 *
 * <p>🔴 AC-1 (existence non-disclosure): {@link #requestSubmit_existingUnknownRateLimited_sameScreen}
 * drives the controller with three different emails while the mocked use case behaves identically
 * (returns normally) for all three — exactly what {@code RequestPasswordResetUseCase.execute()}
 * really does (verified separately by {@code RequestPasswordResetUseCaseTest}) — and asserts the
 * three renders are byte-identical. The controller has no branch that could tell them apart.
 */
@DisplayName("IdP 비밀번호 재설정 화면 (TASK-BE-627)")
class PasswordResetPageSliceTest {

    private static final String TOKEN = "6f1e2d3c-7777-4888-9999-aaaabbbbcccc";

    private RequestPasswordResetUseCase requestPasswordResetUseCase;
    private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        requestPasswordResetUseCase = mock(RequestPasswordResetUseCase.class);
        confirmPasswordResetUseCase = mock(ConfirmPasswordResetUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new PasswordResetPageController(requestPasswordResetUseCase, confirmPasswordResetUseCase))
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

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(csrfAttribute())).andReturn();
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- /password-reset/request

    @Test
    @DisplayName("GET → 이메일 입력 폼(CSRF) · 유스케이스 호출 없음")
    void requestPage_rendersForm() throws Exception {
        MvcResult result = perform(get("/password-reset/request"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String html = html(result);
        assertThat(html).contains("<form action=\"/password-reset/request\" method=\"post\"");
        assertThat(html).contains("name=\"_csrf\" value=\"test-csrf-token\"");
        assertThat(html).contains(">재설정 메일 보내기</button>");
        verifyNoInteractions(requestPasswordResetUseCase);
    }

    @Test
    @DisplayName("이메일 빈칸 → «이메일을 입력해 주세요» · 유스케이스 호출 없음(조회 전 형식 검사)")
    void requestSubmit_blankEmail_doesNotCallUseCase() throws Exception {
        MvcResult result = perform(post("/password-reset/request").param("email", "  "));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(html(result)).contains("이메일을 입력해 주세요");
        verifyNoInteractions(requestPasswordResetUseCase);
    }

    @Test
    @DisplayName("🔴 AC-1: 있는 이메일 · 없는 이메일 · 홍수 제한에 걸린 요청 — 셋 다 똑같은 화면")
    void requestSubmit_existingUnknownRateLimited_sameScreen() throws Exception {
        // RequestPasswordResetUseCase.execute() really does return normally for all three cases
        // (see its Javadoc + RequestPasswordResetUseCaseTest) — doNothing here mirrors that exactly
        // rather than asserting something the controller could not possibly observe otherwise.
        doNothing().when(requestPasswordResetUseCase).execute(any(RequestPasswordResetCommand.class));

        String existing = html(perform(post("/password-reset/request").param("email", "existing@example.com")));
        String unknown = html(perform(post("/password-reset/request").param("email", "ghost@example.com")));
        String rateLimited = html(perform(post("/password-reset/request").param("email", "flooded@example.com")));

        assertThat(existing).contains("계정이 있다면 비밀번호 재설정 메일을 보냈습니다");
        assertThat(existing).as("existing/unknown screens are byte-identical").isEqualTo(unknown);
        assertThat(existing).as("existing/rate-limited screens are byte-identical").isEqualTo(rateLimited);

        verify(requestPasswordResetUseCase).execute(new RequestPasswordResetCommand("existing@example.com"));
        verify(requestPasswordResetUseCase).execute(new RequestPasswordResetCommand("ghost@example.com"));
        verify(requestPasswordResetUseCase).execute(new RequestPasswordResetCommand("flooded@example.com"));
    }

    @Test
    @DisplayName("유스케이스가 예상 못한 예외를 던지면 — «잠시 후 다시» (JSON 500 대신 화면)")
    void requestSubmit_unexpectedException_rendersUnavailable() throws Exception {
        doThrow(new IllegalStateException("redis down"))
                .when(requestPasswordResetUseCase).execute(any(RequestPasswordResetCommand.class));

        MvcResult result = perform(post("/password-reset/request").param("email", "user@example.com"));

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(html(result)).contains("잠시 후 다시 시도해 주세요");
    }

    // ---------------------------------------------------------------- /password-reset

    @Test
    @DisplayName("Edge Case: 토큰 없이 방문 → 오류 화면이 아니라 요청 화면 안내")
    void confirmPage_noToken_showsGuidanceNotError() throws Exception {
        MvcResult result = perform(get("/password-reset"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String html = html(result);
        assertThat(html).contains("비밀번호 재설정 링크가 필요합니다");
        assertThat(html).contains("href=\"/password-reset/request\"");
        assertThat(html).doesNotContain("<form");
        verifyNoInteractions(confirmPasswordResetUseCase);
    }

    @Test
    @DisplayName("토큰 있는 GET → 새 비밀번호 폼 · 토큰은 hidden 필드에만 · 유스케이스 호출 없음")
    void confirmPage_withToken_rendersForm() throws Exception {
        MvcResult result = perform(get("/password-reset").param("token", TOKEN));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String html = html(result);
        assertThat(html).contains("<form action=\"/password-reset\" method=\"post\"");
        assertThat(html).contains("type=\"hidden\" name=\"token\" value=\"" + TOKEN + "\"");
        assertThat(html.split(TOKEN, -1).length - 1).as("R4: the token appears once — the hidden field").isEqualTo(1);
        assertThat(html).contains(">비밀번호 변경</button>");
        verifyNoInteractions(confirmPasswordResetUseCase);
    }

    @Test
    @DisplayName("POST, 토큰 없음 → 요청 화면 안내(GET 과 동일) · 유스케이스 호출 없음")
    void confirmSubmit_noToken_showsGuidance() throws Exception {
        MvcResult result = perform(post("/password-reset")
                .param("newPassword", "NewPassw0rd!").param("confirmPassword", "NewPassw0rd!"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(html(result)).contains("비밀번호 재설정 링크가 필요합니다");
        verifyNoInteractions(confirmPasswordResetUseCase);
    }

    @Test
    @DisplayName("비밀번호 불일치 → «비밀번호가 일치하지 않습니다» · 토큰은 유지 · 유스케이스 호출 없음")
    void confirmSubmit_passwordMismatch_keepsToken() throws Exception {
        MvcResult result = perform(post("/password-reset")
                .param("token", TOKEN).param("newPassword", "NewPassw0rd!").param("confirmPassword", "different!"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String html = html(result);
        assertThat(html).contains("비밀번호가 일치하지 않습니다");
        assertThat(html).contains("type=\"hidden\" name=\"token\" value=\"" + TOKEN + "\"");
        verifyNoInteractions(confirmPasswordResetUseCase);
    }

    @Test
    @DisplayName("만료·사용된 토큰 → 안내 + «재설정 메일 다시 요청» 링크 · 폼 없음(토큰 버림)")
    void confirmSubmit_tokenInvalid_dropsTokenOffersRequestLink() throws Exception {
        doThrow(new PasswordResetTokenInvalidException())
                .when(confirmPasswordResetUseCase).execute(any(ConfirmPasswordResetCommand.class));

        MvcResult result = perform(post("/password-reset")
                .param("token", TOKEN).param("newPassword", "NewPassw0rd!").param("confirmPassword", "NewPassw0rd!"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String html = html(result);
        assertThat(html).contains("링크가 만료되었거나 이미 사용되었습니다");
        assertThat(html).contains("href=\"/password-reset/request\"");
        assertThat(html).doesNotContain("<form").doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("🔴 정책 위반(Failure Scenario 3) → 정책 문구 · 토큰은 유지 · 새 비밀번호 입력은 다시 채우지 않는다")
    void confirmSubmit_policyViolation_keepsTokenNeverRefillsPassword() throws Exception {
        doThrow(new PasswordPolicyViolationException("Password must be at least 8 characters"))
                .when(confirmPasswordResetUseCase).execute(any(ConfirmPasswordResetCommand.class));

        MvcResult result = perform(post("/password-reset")
                .param("token", TOKEN).param("newPassword", "short1A!").param("confirmPassword", "short1A!"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String html = html(result);
        assertThat(html).contains("대문자·소문자·숫자·특수문자");
        assertThat(html).contains("type=\"hidden\" name=\"token\" value=\"" + TOKEN + "\"");
        assertThat(html).as("the submitted password must never be echoed back").doesNotContain("short1A!");
        assertThat(html).doesNotContain("value=\"short1A!\"");
    }

    @Test
    @DisplayName("성공 → /login?passwordReset 로 redirect")
    void confirmSubmit_success_redirectsToLogin() throws Exception {
        doNothing().when(confirmPasswordResetUseCase).execute(any(ConfirmPasswordResetCommand.class));

        MvcResult result = perform(post("/password-reset")
                .param("token", TOKEN).param("newPassword", "NewPassw0rd!").param("confirmPassword", "NewPassw0rd!"));

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?passwordReset");
        verify(confirmPasswordResetUseCase).execute(new ConfirmPasswordResetCommand(TOKEN, "NewPassw0rd!"));
    }

    @Test
    @DisplayName("유스케이스가 예상 못한 예외를 던지면 — 토큰 유지 · «지금은 처리할 수 없습니다»")
    void confirmSubmit_unexpectedException_keepsTokenRendersUnavailable() throws Exception {
        doThrow(new IllegalStateException("db down"))
                .when(confirmPasswordResetUseCase).execute(any(ConfirmPasswordResetCommand.class));

        MvcResult result = perform(post("/password-reset")
                .param("token", TOKEN).param("newPassword", "NewPassw0rd!").param("confirmPassword", "NewPassw0rd!"));

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        String html = html(result);
        assertThat(html).contains("지금은 처리할 수 없습니다");
        assertThat(html).contains("type=\"hidden\" name=\"token\" value=\"" + TOKEN + "\"");
    }
}
