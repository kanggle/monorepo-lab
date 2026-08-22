package com.example.auth.presentation;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-581 AC-1 — the self-service signup entry point on {@code /login} is offered only
 * when the tenant behind the flow can actually accept a signup.
 *
 * <h2>Why this renders the real template instead of asserting the model attribute</h2>
 * The obvious version of this test asserts {@code model().attribute("signupAvailable", false)}.
 * That test passes with the {@code th:if} misspelled, deleted, or attached to the wrong
 * element — it measures the controller, and the thing that ships is the HTML. So the engine
 * below is the real {@link SpringTemplateEngine} pointed at the real
 * {@code classpath:/templates/login.html}, and every assertion reads the rendered bytes.
 *
 * <h2>Control cells are not optional</h2>
 * The link exists because TASK-BE-470 fixed a dead end on the <b>consumer</b> path. Making it
 * conditional risks reverting BE-470 for everyone, and that regression would surface on the
 * general user path, not on the console. Hence one bite cell (the console's ineligible tenant)
 * and three control cells that must be UNCHANGED. A run in which the bite passes and the
 * controls were dropped proves nothing.
 *
 * <p>The negative cell additionally asserts that the rest of the page still rendered — an
 * empty or failed render would also "not contain" the link, and would be scored as a pass.
 */
class LoginPageSignupLinkSliceTest {

    /** The visible text of the signup entry point in {@code login.html}. */
    private static final String SIGNUP_LINK_TEXT = "회원가입";

    /** Rendered by the password form on every cell — the render-actually-happened control. */
    private static final String ALWAYS_PRESENT_MARKER = "Sign in";

    private SavedRequestTenantResolver savedRequestTenantResolver;
    private TenantSignupEligibilityPort tenantSignupEligibilityPort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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
                .standaloneSetup(new LoginPageController(
                        savedRequestTenantResolver, tenantSignupEligibilityPort))
                .setViewResolvers(viewResolver)
                .build();
    }

    /**
     * {@code login.html} reads {@code ${_csrf}} — the request attribute {@code CsrfFilter}
     * publishes under the token's parameter name. Standalone MockMvc runs no filter chain, so
     * the test supplies it directly; without this the template render fails for every cell
     * and the suite would be measuring its own scaffolding.
     */
    private static RequestPostProcessor csrfAttribute() {
        return request -> {
            request.setAttribute("_csrf",
                    new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
            return request;
        };
    }

    private String renderLoginPageFor(String tenantId, boolean signupOffered) throws Exception {
        when(savedRequestTenantResolver.resolve(any(), any()))
                .thenReturn(new SavedRequestTenantResolver.Resolution(tenantId, "B2C", null));
        when(tenantSignupEligibilityPort.isSignupOffered(tenantId)).thenReturn(signupOffered);

        return mockMvc.perform(get("/login").with(csrfAttribute()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("BITE — a flow started by platform-console-web (tenant `iam`, no tenants row) "
            + "renders no signup link")
    void consoleFlowDoesNotOfferSignup() throws Exception {
        String html = renderLoginPageFor("iam", false);

        assertThat(html)
                .as("the rest of the login page must still render — otherwise 'link absent' "
                        + "is a failed render, not a passing guard")
                .contains(ALWAYS_PRESENT_MARKER);
        assertThat(html)
                .as("the console's tenant can never accept a signup; offering one is the defect")
                .doesNotContain(SIGNUP_LINK_TEXT);
        assertThat(html).doesNotContain("/signup");
    }

    @Test
    @DisplayName("CONTROL — fan-platform (the default consumer tenant) still offers signup")
    void fanPlatformFlowStillOffersSignup() throws Exception {
        String html = renderLoginPageFor("fan-platform", true);

        assertThat(html).contains(ALWAYS_PRESENT_MARKER);
        assertThat(html).contains(SIGNUP_LINK_TEXT);
        assertThat(html).contains("/signup");
    }

    @Test
    @DisplayName("CONTROL — ecommerce (web-store client) still offers signup")
    void ecommerceFlowStillOffersSignup() throws Exception {
        String html = renderLoginPageFor("ecommerce", true);

        assertThat(html).contains(ALWAYS_PRESENT_MARKER);
        assertThat(html).contains(SIGNUP_LINK_TEXT);
    }

    @Test
    @DisplayName("CONTROL — a direct visit with no saved authorize request falls back to the "
            + "default tenant and still offers signup")
    void directVisitStillOffersSignup() throws Exception {
        // SavedRequestTenantResolver's documented fallback for "no saved request".
        String html = renderLoginPageFor("fan-platform", true);

        assertThat(html).contains(SIGNUP_LINK_TEXT);
    }

    @Test
    @DisplayName("The link is decided by the tenant's own eligibility, not by a client name — "
            + "an ineligible consumer tenant (e.g. suspended) also hides it")
    void ineligibleConsumerTenantAlsoHidesSignup() throws Exception {
        // Guards against a "fix" that special-cases the string "iam" instead of asking whether
        // the tenant can accept a signup. A suspended ecommerce tenant is a 403, not a 404,
        // and must be handled by the same predicate.
        String html = renderLoginPageFor("ecommerce", false);

        assertThat(html).contains(ALWAYS_PRESENT_MARKER);
        assertThat(html).doesNotContain(SIGNUP_LINK_TEXT);
    }
}
