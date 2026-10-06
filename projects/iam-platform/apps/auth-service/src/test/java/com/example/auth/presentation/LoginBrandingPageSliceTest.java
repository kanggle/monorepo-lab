package com.example.auth.presentation;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantSignupEligibilityPort;
import com.example.auth.infrastructure.oauth.OAuthProperties;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.example.auth.infrastructure.security.LoginBranding;
import com.example.auth.infrastructure.security.LoginBrandingResolver;
import com.example.auth.infrastructure.security.SavedRequestTenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-613 (ADR-007) — the rendered {@code /login} and {@code /signup} pages wear the
 * initiating client's brand, and the form those pages carry is unchanged.
 *
 * <p>Like {@code LoginPageSignupLinkSliceTest}, this renders the REAL templates (and the shared
 * {@code fragments/auth-page.html}) and reads the bytes: a model-attribute assertion would pass
 * with the template ignoring {@code branding} altogether.
 *
 * <h2>The form contract (ADR-007 invariant 3)</h2>
 * web-store e2e, console-web e2e and {@code scripts/capture-portfolio.mjs} drive the login form
 * by {@code #username}, {@code #password}, {@code form[action="/login"]} and
 * {@code button[type="submit"]}. The cells below pin those selectors as they are written there,
 * including that each form holds exactly ONE submit button — the new password toggle is a
 * {@code <button>}, and a {@code <button>} without {@code type} is a submit button.
 */
class LoginBrandingPageSliceTest {

    private SavedRequestTenantResolver savedRequestTenantResolver;
    private MockMvc loginMvc;
    private MockMvc signupMvc;

    @BeforeEach
    void setUp() {
        savedRequestTenantResolver = mock(SavedRequestTenantResolver.class);
        when(savedRequestTenantResolver.resolve(any(), any()))
                .thenReturn(new SavedRequestTenantResolver.Resolution("fan-platform", "B2C", null));
        TenantSignupEligibilityPort eligibility = mock(TenantSignupEligibilityPort.class);
        when(eligibility.isSignupOffered(any())).thenReturn(true);
        LoginBrandingResolver brandingResolver = new LoginBrandingResolver(savedRequestTenantResolver);

        // TASK-BE-623: this suite renders the real login.html and asserts on
        // href="/login/oauth/google" (loginFormContract below) — google must read as
        // "configured" or the button (and the "또는 다음으로 계속" divider) would not render.
        OAuthProperties oAuthProperties = new OAuthProperties();
        oAuthProperties.getGoogle().setClientId("real-client-id");
        oAuthProperties.getGoogle().setClientSecret("real-client-secret");

        loginMvc = MockMvcBuilders
                .standaloneSetup(new LoginPageController(
                        savedRequestTenantResolver, eligibility, brandingResolver, oAuthProperties))
                .setViewResolvers(realTemplates())
                .build();
        signupMvc = MockMvcBuilders
                .standaloneSetup(new SignupPageController(mock(AccountServicePort.class),
                        savedRequestTenantResolver, eligibility, brandingResolver))
                .setViewResolvers(realTemplates())
                .build();
    }

    /** One per MockMvc — a view resolver binds to the first stub context it is given. */
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
            request.setAttribute("_csrf",
                    new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
            return request;
        };
    }

    private void givenInitiatingClient(Map<String, Object> branding) {
        ClientSettings.Builder settings = ClientSettings.builder();
        branding.forEach(settings::setting);
        RegisteredClient client = RegisteredClient.withId("id-1")
                .clientId("some-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://app.local/callback")
                .scope("openid")
                .clientSettings(settings.build())
                .build();
        when(savedRequestTenantResolver.initiatingClient(any(), any())).thenReturn(Optional.of(client));
    }

    private static Map<String, Object> consoleBranding() {
        return Map.of(
                OAuthClientMapper.SETTING_BRANDING_SERVICE_NAME, "IAM",
                OAuthClientMapper.SETTING_BRANDING_TITLE, "IAM 로그인",
                OAuthClientMapper.SETTING_BRANDING_DESCRIPTION, "운영자 계정으로 로그인합니다",
                OAuthClientMapper.SETTING_BRANDING_LOGO, "console",
                OAuthClientMapper.SETTING_BRANDING_PRIMARY_COLOR, "#171717");
    }

    private static Map<String, Object> gapBranding() {
        return Map.of(
                OAuthClientMapper.SETTING_BRANDING_SERVICE_NAME, "GAP",
                OAuthClientMapper.SETTING_BRANDING_TITLE, "GAP로 로그인",
                OAuthClientMapper.SETTING_BRANDING_DESCRIPTION, "GAP으로 안전하게 로그인합니다",
                OAuthClientMapper.SETTING_BRANDING_PRIMARY_COLOR, "#9333ea");
    }

    private static String render(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(csrfAttribute()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String login(MockMvc mvc) throws Exception {
        return render(mvc, get("/login"));
    }

    /**
     * The page with {@code <script>}, {@code <style>} and comments removed — what a DOM selector
     * can match. Page-wide counts must use this: the enhance script itself contains the string
     * {@code button[type="submit"]}, and the stylesheet names {@code aria-invalid}.
     */
    private static String markup(String html) {
        return html.replaceAll("(?s)<script\\b.*?</script>", "")
                .replaceAll("(?s)<style\\b.*?</style>", "")
                .replaceAll("(?s)<!--.*?-->", "");
    }

    /** The bodies of every inline {@code <script>}. */
    private static String scripts(String html) {
        Matcher m = Pattern.compile("<script\\b[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
        StringBuilder all = new StringBuilder();
        while (m.find()) {
            all.append(m.group(1));
        }
        return all.toString();
    }

    private static String only(String html, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(html);
        assertThat(m.find()).as("expected a match for %s", regex).isTrue();
        return m.group(1);
    }

    private static int count(String html, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(html);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** The body of every {@code <form ...>...</form>} on the page. */
    private static java.util.List<String> forms(String html) {
        Matcher m = Pattern.compile("<form\\b[^>]*>(.*?)</form>", Pattern.DOTALL).matcher(html);
        java.util.List<String> bodies = new java.util.ArrayList<>();
        while (m.find()) {
            bodies.add(m.group(0));
        }
        return bodies;
    }

    @Test
    @DisplayName("console flow → IAM title, heading, subtitle, console logo and colour")
    void consoleFlow() throws Exception {
        givenInitiatingClient(consoleBranding());

        String html = login(loginMvc);

        assertThat(only(html, "<title>(.*?)</title>")).isEqualTo("IAM 로그인");
        assertThat(only(html, "<h1>(.*?)</h1>")).isEqualTo("IAM 로그인");
        assertThat(html).contains("운영자 계정으로 로그인합니다");
        assertThat(html).contains("data-logo=\"console\"");
        assertThat(html).contains("--brand: #171717");
    }

    @Test
    @DisplayName("a client with its own brand (sample: GAP) → that brand; no logo configured, so none is drawn")
    void fanFlow() throws Exception {
        givenInitiatingClient(gapBranding());

        String html = login(loginMvc);

        assertThat(only(html, "<title>(.*?)</title>")).isEqualTo("GAP로 로그인");
        assertThat(only(html, "<h1>(.*?)</h1>")).isEqualTo("GAP로 로그인");
        assertThat(html).contains("GAP으로 안전하게 로그인합니다");
        assertThat(html).doesNotContain("data-logo=");
        assertThat(html).contains("--brand: #9333ea");
        assertThat(html).doesNotContain("Global Account");
    }

    @Test
    @DisplayName("no identifiable client → IAM default (D3 as accepted), no subtitle, default colour")
    void defaultBranding() throws Exception {
        String html = login(loginMvc);

        assertThat(only(html, "<title>(.*?)</title>")).isEqualTo("IAM 로그인");
        assertThat(only(html, "<h1>(.*?)</h1>")).isEqualTo("IAM 로그인");
        assertThat(html).doesNotContain("class=\"description\"");
        assertThat(html).contains("--brand: " + LoginBranding.DEFAULT_PRIMARY_COLOR);
        assertThat(html).doesNotContain("Global Account");
    }

    @Test
    @DisplayName("BITE (invariant 2) — a branding value is printed as text, never as markup")
    void brandingIsEscaped() throws Exception {
        givenInitiatingClient(Map.of(
                OAuthClientMapper.SETTING_BRANDING_TITLE, "<script>alert(1)</script>",
                OAuthClientMapper.SETTING_BRANDING_DESCRIPTION, "\"><img src=x onerror=alert(1)>"));

        String html = login(loginMvc);

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).doesNotContain("<img src=x");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("every allowlisted logo name has an inline SVG — the two lists cannot drift")
    void everyAllowlistedLogoRenders() throws Exception {
        for (String logo : LoginBranding.ALLOWED_LOGOS) {
            givenInitiatingClient(Map.of(OAuthClientMapper.SETTING_BRANDING_LOGO, logo));
            assertThat(login(loginMvc)).as("logo %s", logo).contains("data-logo=\"" + logo + "\"");
        }
    }

    @Test
    @DisplayName("CONTRACT (invariant 3) — the login form is still driven by the same selectors")
    void loginFormContract() throws Exception {
        givenInitiatingClient(gapBranding());

        String html = login(loginMvc);

        java.util.List<String> forms = forms(html);
        assertThat(forms).hasSize(1);
        String form = forms.get(0);
        // web-store e2e/helpers/auth.ts: form[action="/login"] button[type="submit"]
        assertThat(form).startsWith("<form action=\"/login\" method=\"post\"");
        assertThat(count(form, "type=\"submit\"")).isEqualTo(1);
        // every <button> in the form declares its type — a bare <button> would submit
        assertThat(count(form, "<button\\b")).isEqualTo(count(form, "<button\\b[^>]*\\btype=\""));
        assertThat(count(form, "<button\\b[^>]*type=\"button\"")).isEqualTo(1);
        assertThat(form).contains("type=\"email\" id=\"username\" name=\"username\"");
        assertThat(form).contains("type=\"password\" id=\"password\" name=\"password\"");
        assertThat(form).contains("name=\"_csrf\" value=\"test-csrf-token\"");
        assertThat(html).contains("href=\"/login/oauth/google\"");
        // console-web tests/e2e/fixtures/login.ts clicks page-wide button[type="submit"]
        assertThat(count(markup(html), "type=\"submit\"")).isEqualTo(1);
    }

    @Test
    @DisplayName("the password toggle is hidden until the script enables it (works without JS)")
    void toggleIsProgressiveEnhancement() throws Exception {
        String html = login(loginMvc);

        assertThat(only(html, "(<button type=\"button\" class=\"password-toggle\"[^>]*>)"))
                .contains("aria-controls=\"password\"")
                .contains("aria-pressed=\"false\"")
                .contains("hidden");
    }

    @Test
    @DisplayName("an error is announced (role=alert) and wired to the inputs (aria-describedby)")
    void errorIsAccessible() throws Exception {
        String html = render(loginMvc, get("/login").param("error", ""));

        assertThat(html).contains("id=\"form-error\" role=\"alert\"");
        assertThat(html).contains("이메일 또는 비밀번호가 올바르지 않습니다.");
        assertThat(count(markup(html), "aria-describedby=\"form-error\"")).isEqualTo(2);
        assertThat(count(markup(html), "aria-invalid=\"true\"")).isEqualTo(2);
    }

    @Test
    @DisplayName("no error → no aria-invalid / aria-describedby pointing at a missing element")
    void noErrorNoDanglingReferences() throws Exception {
        String html = login(loginMvc);

        assertThat(markup(html)).doesNotContain("aria-invalid");
        assertThat(markup(html)).doesNotContain("aria-describedby=\"form-error\"");
    }

    @Test
    @DisplayName("D4 ④ — the login page is Korean: no English UI strings left")
    void loginPageIsKorean() throws Exception {
        String html = render(loginMvc, get("/login").param("logout", ""));

        assertThat(html).contains("<html lang=\"ko\"");
        assertThat(html).contains("로그아웃되었습니다.");
        for (String english : new String[]{"Sign in", "Email", "Password", "or continue with",
                "signed out", "Invalid email"}) {
            assertThat(html).as("English UI string %s", english).doesNotContain(">" + english);
        }
    }

    @Test
    @DisplayName("D5 — the signup page wears the same brand (GET)")
    void signupPageIsBranded() throws Exception {
        givenInitiatingClient(gapBranding());

        String html = render(signupMvc, get("/signup"));

        assertThat(only(html, "<title>(.*?)</title>")).isEqualTo("GAP 회원가입");
        assertThat(only(html, "<h1>(.*?)</h1>")).isEqualTo("GAP 회원가입");
        assertThat(html).contains("--brand: #9333ea");
        assertThat(html)
                .as("the configured subtitle is worded for signing in — not shown on signup")
                .doesNotContain("GAP으로 안전하게 로그인합니다");
        assertThat(html).doesNotContain("Global Account");
    }

    @Test
    @DisplayName("D5 — an error re-render of POST /signup keeps the brand")
    void signupErrorReRenderIsBranded() throws Exception {
        givenInitiatingClient(gapBranding());

        String html = render(signupMvc, post("/signup")
                .param("email", "visitor@example.com")
                .param("password", "short")
                .param("confirmPassword", "short"));

        assertThat(html).contains("비밀번호는 8자 이상이어야 합니다.");
        assertThat(only(html, "<h1>(.*?)</h1>")).isEqualTo("GAP 회원가입");
    }

    @Test
    @DisplayName("CONTRACT — the signup form keeps one submit button and typed toggles")
    void signupFormContract() throws Exception {
        String html = render(signupMvc, get("/signup"));

        java.util.List<String> forms = forms(html);
        assertThat(forms).hasSize(1);
        String form = forms.get(0);
        assertThat(count(form, "type=\"submit\"")).isEqualTo(1);
        assertThat(count(form, "<button\\b")).isEqualTo(count(form, "<button\\b[^>]*\\btype=\""));
        assertThat(count(form, "<button\\b[^>]*type=\"button\"")).isEqualTo(2);
        assertThat(form).contains("id=\"email\" name=\"email\"");
        assertThat(form).contains("id=\"password\" name=\"password\"");
        assertThat(form).contains("id=\"confirmPassword\" name=\"confirmPassword\"");
        assertThat(scripts(html)).as("the pre-check reports in the page, not in a dialog")
                .doesNotContain("alert(");
    }
}
