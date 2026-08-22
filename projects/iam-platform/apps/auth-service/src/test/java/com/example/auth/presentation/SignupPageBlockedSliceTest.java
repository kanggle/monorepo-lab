package com.example.auth.presentation;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantSignupEligibilityPort;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-581 — the {@code /signup} surface itself refuses an impossible signup.
 *
 * <h2>Why hiding the link on {@code /login} is not enough</h2>
 * {@code GET}/{@code POST /signup} are {@code permitAll} on the {@code @Order(0)} chain, so
 * the page stays reachable by typed URL, bookmark and back-button while the console's saved
 * {@code /oauth2/authorize} request is still in session. On that path the pre-581 code called
 * account-service, took a permanent {@code 404 TENANT_NOT_FOUND}, and reported it as
 * "the authentication service is temporarily unavailable" — which is the reported defect,
 * verbatim. A link-only fix leaves that path intact and quietly hands the whole problem to
 * TASK-BE-580's wording change.
 *
 * <h2>The assertion that matters most</h2>
 * {@code accountServicePort.signup(...)} must never be called for an ineligible tenant. If it
 * were, this test could still be green on the message while the request still travelled — and
 * the "permanent" claim would be an assertion about text, not about behaviour.
 *
 * <p>Sibling: {@code LoginPageSignupLinkSliceTest} covers the offer; this covers the act.
 * Neither alone closes AC-1 — the surfaces are independent and only their intersection is the
 * user's path.
 */
class SignupPageBlockedSliceTest {

    private static final String SIGNUP_FORM_MARKER = "id=\"signup-form\"";

    /** Rendered by the page shell on every cell — the render-actually-happened control. */
    private static final String ALWAYS_PRESENT_MARKER = "Create your Global Account";

    private AccountServicePort accountServicePort;
    private SavedRequestTenantResolver savedRequestTenantResolver;
    private TenantSignupEligibilityPort tenantSignupEligibilityPort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountServicePort = mock(AccountServicePort.class);
        savedRequestTenantResolver = mock(SavedRequestTenantResolver.class);
        tenantSignupEligibilityPort = mock(TenantSignupEligibilityPort.class);

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
                .standaloneSetup(new SignupPageController(
                        accountServicePort, savedRequestTenantResolver, tenantSignupEligibilityPort))
                .setViewResolvers(viewResolver)
                .build();
    }

    /** See {@code LoginPageSignupLinkSliceTest#csrfAttribute()} — same reason. */
    private static RequestPostProcessor csrfAttribute() {
        return request -> {
            request.setAttribute("_csrf",
                    new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
            return request;
        };
    }

    /**
     * Returns ONLY the text of the server-rendered {@code .error} element.
     *
     * <p>Measured, not assumed: asserting {@code doesNotContain("패스워드는 8자 이상이어야 합니다")}
     * over the whole page fails even when the server never produced that message, because the
     * page also ships an inline client-side pre-check whose {@code alert()} carries the same
     * sentence. Two sources, one string — so a whole-page predicate cannot tell which one
     * spoke. Read the element that carries the server's verdict.
     */
    private static String serverErrorText(String html) {
        Matcher m = Pattern.compile("<div class=\"error\"[^>]*>(.*?)</div>", Pattern.DOTALL)
                .matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private void givenTenant(String tenantId, boolean signupOffered) {
        when(savedRequestTenantResolver.resolve(any(), any()))
                .thenReturn(new SavedRequestTenantResolver.Resolution(tenantId, "B2C", null));
        when(tenantSignupEligibilityPort.isSignupOffered(tenantId)).thenReturn(signupOffered);
    }

    @Test
    @DisplayName("BITE — GET /signup inside a console flow renders no form and says so permanently")
    void getSignupInConsoleFlowRendersNoForm() throws Exception {
        givenTenant("iam", false);

        String html = mockMvc.perform(get("/signup").with(csrfAttribute()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("the page shell must still render — otherwise 'no form' is a failed render")
                .contains(ALWAYS_PRESENT_MARKER);
        assertThat(html).doesNotContain(SIGNUP_FORM_MARKER);
        assertThat(serverErrorText(html))
                .as("the message must not tell the visitor to retry a permanently impossible act")
                .doesNotContain("잠시 후 다시")
                .contains("관리자");
    }

    @Test
    @DisplayName("BITE — POST /signup for an ineligible tenant never reaches account-service")
    void postSignupForIneligibleTenantIsNotProxied() throws Exception {
        givenTenant("iam", false);

        String html = mockMvc.perform(post("/signup").with(csrfAttribute())
                        .param("email", "visitor@example.com")
                        .param("displayName", "Visitor")
                        .param("password", "Str0ng!pass")
                        .param("confirmPassword", "Str0ng!pass"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verify(accountServicePort, never()).signup(any(), any(), any(), any());
        assertThat(serverErrorText(html)).doesNotContain("잠시 후 다시").contains("관리자");
        assertThat(html).doesNotContain(SIGNUP_FORM_MARKER);
    }

    @Test
    @DisplayName("BITE — the refusal precedes field validation, so an invalid password does not "
            + "mask the real reason")
    void refusalPrecedesFieldValidation() throws Exception {
        givenTenant("iam", false);

        String html = mockMvc.perform(post("/signup").with(csrfAttribute())
                        .param("email", "not-an-email")
                        .param("password", "short")
                        .param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Every field above is invalid. The visitor must still be told the thing they can act
        // on — fixing the password would not make this signup possible.
        assertThat(serverErrorText(html))
                .contains("관리자")
                .doesNotContain("패스워드는 8자 이상이어야 합니다")
                .doesNotContain("이메일 형식이 올바르지 않습니다");
        verify(accountServicePort, never()).signup(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CONTROL — GET /signup on a consumer flow still renders the form")
    void getSignupOnConsumerFlowStillRendersForm() throws Exception {
        givenTenant("ecommerce", true);

        String html = mockMvc.perform(get("/signup").with(csrfAttribute()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(ALWAYS_PRESENT_MARKER);
        assertThat(html).contains(SIGNUP_FORM_MARKER);
    }

    @Test
    @DisplayName("CONTROL — POST /signup on a consumer flow still proxies to account-service "
            + "with that flow's tenant")
    void postSignupOnConsumerFlowStillProxies() throws Exception {
        givenTenant("ecommerce", true);

        mockMvc.perform(post("/signup").with(csrfAttribute())
                        .param("email", "shopper@example.com")
                        .param("displayName", "Shopper")
                        .param("password", "Str0ng!pass")
                        .param("confirmPassword", "Str0ng!pass"))
                .andExpect(status().is3xxRedirection());

        verify(accountServicePort).signup("shopper@example.com", "Str0ng!pass", "Shopper", "ecommerce");
    }
}
