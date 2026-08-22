package com.example.auth.presentation;

import com.example.auth.application.port.TenantSignupEligibilityPort;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.infrastructure.security.SavedRequestTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;

/**
 * TASK-BE-396 (ADR-006 option B) — renders the custom {@code /login} page.
 *
 * <p>Replaces Spring Security's {@code DefaultLoginPageGeneratingFilter}: the
 * Thymeleaf {@code login} view carries the existing email/password form AND the
 * social-login buttons (Google / Kakao / Microsoft), which the default form
 * cannot render. {@code WebLoginSecurityConfig} now calls
 * {@code .loginPage("/login")} (suppressing the default form) so this controller
 * owns the GET {@code /login} view.
 *
 * <p>The chain stays at {@code @Order(0)}; this controller only renders HTML.
 * POST {@code /login} is still handled by the form-login filter
 * ({@code UsernamePasswordAuthenticationFilter}) bridged through
 * {@code CredentialAuthenticationProvider}.
 */
@Controller
@RequiredArgsConstructor
public class LoginPageController {

    /**
     * The enabled social providers, sourced from the {@link OAuthProvider} enum
     * (Naver is a future addition — TASK-BE-397). Lowercased for the
     * {@code /login/oauth/{provider}} link path.
     */
    private static final List<ProviderView> PROVIDERS = Arrays.stream(OAuthProvider.values())
            .map(p -> new ProviderView(p.name(), p.name().toLowerCase()))
            .toList();

    /**
     * TASK-BE-581: the tenant this browser flow would create an account in, derived from the
     * OIDC client that initiated it. The same resolver {@code SignupPageController} uses to
     * decide where the account is born — so the offer and the act cannot disagree.
     */
    private final SavedRequestTenantResolver savedRequestTenantResolver;

    /** TASK-BE-581: whether signup may be OFFERED for that tenant. */
    private final TenantSignupEligibilityPort tenantSignupEligibilityPort;

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "logout", required = false) String logout,
            @RequestParam(name = "registered", required = false) String registered,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        model.addAttribute("providers", PROVIDERS);
        model.addAttribute("error", error);
        model.addAttribute("loggedOut", logout != null);
        // TASK-BE-470: the signup page redirects here with ?registered on success.
        model.addAttribute("registered", registered != null);
        // TASK-BE-581: the signup entry point is conditional. A flow started by a client
        // whose tenant cannot accept signups (the console's reserved `iam` slug; a suspended
        // tenant) must not be offered a form that is guaranteed to fail. Consumer clients and
        // a direct visit with no saved authorize request are unaffected — they resolve to a
        // real ACTIVE tenant and still see the link (TASK-BE-470 preserved).
        String tenantId = savedRequestTenantResolver.resolve(request, response).tenantId();
        model.addAttribute("signupAvailable",
                tenantSignupEligibilityPort.isSignupOffered(tenantId));
        // The password form posts to the form-login filter's default URL.
        model.addAttribute("passwordFormAction", "/login");
        return "login";
    }

    /**
     * View model for a single social-login button.
     *
     * @param name  the provider display/enum name (e.g. {@code GOOGLE})
     * @param slug  the lowercase path segment (e.g. {@code google}) used in
     *              {@code /login/oauth/{slug}}
     */
    public record ProviderView(String name, String slug) {
    }
}
