package com.example.auth.presentation;

import com.example.auth.application.port.TenantSignupEligibilityPort;
import com.example.auth.infrastructure.oauth.OAuthProperties;
import com.example.auth.infrastructure.security.LoginBrandingResolver;
import com.example.auth.infrastructure.security.SavedRequestTenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-627 — {@code login.html} gets a «비밀번호를 잊으셨나요?» link to
 * {@code /password-reset/request}, and shows a notice after {@code PasswordResetPageController}
 * redirects here with {@code ?passwordReset}. Renders the REAL template (same rationale as
 * {@link LoginPageSignupLinkSliceTest}): a model-attribute assertion would pass even if the
 * {@code th:if}/link were deleted from the HTML.
 *
 * <p>ADR-007 invariant 3 control: the password form must still carry exactly {@code #username} /
 * {@code #password}, the CSRF field, {@code POST /login}, and exactly ONE
 * {@code button[type=submit]} — the new link/notice are additions, not replacements, and must not
 * turn the forgot-password {@code <a>} into a second submit control.
 */
class LoginPagePasswordResetLinkSliceTest {

    private static final String FORGOT_PASSWORD_LINK_TEXT = "비밀번호를 잊으셨나요?";
    private static final Pattern SUBMIT_BUTTON =
            Pattern.compile("<button[^>]*type=\"submit\"[^>]*>", Pattern.DOTALL);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SavedRequestTenantResolver savedRequestTenantResolver = mock(SavedRequestTenantResolver.class);
        when(savedRequestTenantResolver.resolve(any(), any()))
                .thenReturn(new SavedRequestTenantResolver.Resolution("fan-platform", "B2C", null));
        TenantSignupEligibilityPort tenantSignupEligibilityPort = mock(TenantSignupEligibilityPort.class);
        when(tenantSignupEligibilityPort.isSignupOffered(any())).thenReturn(true);

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

        mockMvc = MockMvcBuilders
                .standaloneSetup(new LoginPageController(
                        savedRequestTenantResolver, tenantSignupEligibilityPort,
                        new LoginBrandingResolver(savedRequestTenantResolver),
                        new OAuthProperties()))
                .setViewResolvers(viewResolver)
                .build();
    }

    private static RequestPostProcessor csrfAttribute() {
        return request -> {
            request.setAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
            return request;
        };
    }

    @Test
    @DisplayName("GET /login → «비밀번호를 잊으셨나요?» 링크(/password-reset/request) · 폼 계약은 그대로")
    void loginPage_showsForgotPasswordLink_formContractUnchanged() throws Exception {
        String html = mockMvc.perform(get("/login").with(csrfAttribute()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains(FORGOT_PASSWORD_LINK_TEXT);
        assertThat(html).contains("href=\"/password-reset/request\"");
        // ADR-007 invariant 3: #username / #password / CSRF field / POST /login / exactly one
        // submit button. The link is an <a>, not a <button type="submit">.
        assertThat(html).contains("id=\"username\"").contains("id=\"password\"");
        assertThat(html).contains("name=\"_csrf\" value=\"test-csrf-token\"");
        long submitButtons = SUBMIT_BUTTON.matcher(html).results().count();
        assertThat(submitButtons).as("exactly one submit button on the password form").isEqualTo(1);
    }

    @Test
    @DisplayName("GET /login (passwordReset 없음) → 재설정 안내 없음")
    void loginPage_withoutPasswordResetParam_showsNoNotice() throws Exception {
        String html = mockMvc.perform(get("/login").with(csrfAttribute()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("비밀번호를 변경했습니다");
    }

    @Test
    @DisplayName("GET /login?passwordReset → «비밀번호를 변경했습니다» 안내")
    void loginPage_withPasswordResetParam_showsNotice() throws Exception {
        String html = mockMvc.perform(get("/login").param("passwordReset", "").with(csrfAttribute()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.");
    }
}
