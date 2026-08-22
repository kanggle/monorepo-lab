package com.example.auth.presentation;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.SignupEmailConflictException;
import com.example.auth.application.exception.SignupInvalidException;
import com.example.auth.application.exception.SignupNotPossibleException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantSignupEligibilityPort;
import com.example.auth.infrastructure.security.SavedRequestTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.regex.Pattern;

/**
 * TASK-BE-470 / TASK-BE-470-fix-001 — the browser self-service signup surface.
 *
 * <p>Sibling of {@link LoginPageController}. {@code GET /signup} renders the
 * Thymeleaf {@code signup} form; {@code POST /signup} is a <b>server-side proxy</b>
 * that calls account-service {@code POST /api/accounts/signup} via
 * {@link AccountServicePort#signup} and then redirects to {@code /login?registered}.
 *
 * <p><b>Why a server-side proxy (fix-001).</b> The original design had the form's
 * client-side {@code fetch} POST directly to {@code /api/accounts/signup}, assuming it
 * was same-origin with this page. It is not: the SAS browser pages ({@code /login},
 * {@code /signup}) are served on the auth-service origin, while {@code /api/accounts}
 * lives behind the IAM gateway / account-service — the gateway does not proxy
 * {@code /login|/signup}, and account-service sets no CORS. So a relative fetch hit
 * auth-service (404/403) and a cross-origin fetch would be CORS-blocked. Proxying the
 * call server-side keeps the whole flow same-origin, exactly like the {@code /login}
 * form. auth-service already depends on account-service ({@link AccountServicePort}),
 * so this introduces no new coupling.
 *
 * <p>{@code GET}/{@code POST /signup} are both public — added to
 * {@code WebLoginSecurityConfig}'s {@code @Order(0)} chain {@code securityMatcher}
 * ({@code anyRequest().permitAll()}). CSRF is enabled on that chain, so the form
 * carries the {@code _csrf} token (like {@code login.html}).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SignupPageController {

    private final AccountServicePort accountServicePort;

    /**
     * TASK-BE-507: resolves the tenant of the OIDC client that sent the user here, from the
     * saved {@code /oauth2/authorize} request. {@code SocialLoginBrowserController} has used
     * this resolver all along (for the social-identity row and the token) — the form-signup
     * path did not, which is why every consumer, ecommerce shoppers included, was born
     * {@code fan-platform}. A direct visit to {@code /signup} (no saved authorize request)
     * resolves to {@code fan-platform}, the pre-BE-507 default.
     */
    private final SavedRequestTenantResolver savedRequestTenantResolver;

    /**
     * TASK-BE-581: whether the resolved tenant can accept a signup at all. The link on
     * {@code login.html} is hidden for an ineligible tenant, but {@code /signup} is
     * {@code permitAll} and reachable by URL, bookmark, or back-button — so hiding the link
     * alone would leave the defect on exactly the path a returning visitor takes. The gate
     * lives on BOTH surfaces on purpose.
     */
    private final TenantSignupEligibilityPort tenantSignupEligibilityPort;

    /**
     * Shown when signup cannot succeed for this tenant, whether that was decided <b>before</b>
     * the call (TASK-BE-581's eligibility gate) or reported <b>by</b> the call
     * (TASK-BE-580's {@link SignupNotPossibleException}).
     *
     * <p>🔴 One constant for both on purpose. They are the same situation from the visitor's
     * side, and the repository has been bitten by the same fact living in two places where
     * only one of them later gets fixed. If this wording ever needs to differ per cause, split
     * it deliberately — do not let it drift.
     *
     * <p>Deliberately NOT "try again later": the condition is structural (a reserved slug has
     * no tenants row and may never get one) or administrative (a suspended tenant). It also
     * has to be more than "an error occurred" — that leaves the visitor with nothing to do —
     * so it names the next step.
     */
    private static final String SIGNUP_NOT_AVAILABLE_MESSAGE =
            "이 경로로는 회원가입할 수 없습니다. 계정 생성은 관리자에게 문의해 주세요.";

    /**
     * TASK-BE-472: mirror account-service's {@code Email} value-object regex
     * ({@code account.domain.account.Email}) so a malformed email is caught here with a
     * precise message instead of being proxied and coming back as an opaque 400 that the
     * {@link SignupInvalidException} branch would otherwise misreport as a password problem.
     * Kept byte-identical to the backend pattern (ASCII, TLD required) so this pre-check never
     * rejects an address account-service would accept.
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    @GetMapping("/signup")
    public String signupPage(HttpServletRequest request, HttpServletResponse response,
                             Model model) {
        applySignupAvailability(request, response, model);
        return "signup";
    }

    /**
     * Resolves the tenant this flow would create the account in and records on the model
     * whether signup is possible there at all. Sets {@code signupBlocked} unconditionally so
     * the template never has to interpret an absent attribute.
     *
     * @return {@code true} when signup is blocked for this flow
     */
    private boolean applySignupAvailability(HttpServletRequest request,
                                            HttpServletResponse response, Model model) {
        String tenantId = savedRequestTenantResolver.resolve(request, response).tenantId();
        boolean blocked = !tenantSignupEligibilityPort.isSignupOffered(tenantId);
        model.addAttribute("signupBlocked", blocked);
        if (blocked) {
            model.addAttribute("error", SIGNUP_NOT_AVAILABLE_MESSAGE);
            log.info("Signup not available for tenantId={} — form suppressed", tenantId);
        }
        return blocked;
    }

    @PostMapping("/signup")
    public String submitSignup(
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "displayName", required = false) String displayName,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        // TASK-BE-581: refuse before validating anything. A tenant that cannot accept a
        // signup makes every field irrelevant, and validating first would answer a question
        // the visitor cannot act on ("your password is too short") ahead of the one they can.
        if (applySignupAvailability(request, response, model)) {
            return "signup";
        }

        String normalizedEmail = email == null ? "" : email.trim();
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();

        // Re-render helper: preserve email/displayName so the user does not retype them.
        model.addAttribute("email", normalizedEmail);
        model.addAttribute("displayName", normalizedDisplayName);

        // Server-side validation (source of truth; the page also pre-checks client-side).
        if (normalizedEmail.isEmpty() || password == null || password.isEmpty()) {
            model.addAttribute("error", "이메일과 패스워드를 입력해 주세요.");
            return "signup";
        }
        // TASK-BE-472: reject a malformed email here so the user gets an email-specific message.
        // account-service would 400 on the same address, but that 400 is mapped to
        // SignupInvalidException below and would otherwise be misreported as a password problem.
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            model.addAttribute("error", "이메일 형식이 올바르지 않습니다.");
            return "signup";
        }
        if (password.length() < 8) {
            model.addAttribute("error", "패스워드는 8자 이상이어야 합니다.");
            return "signup";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "패스워드가 일치하지 않습니다.");
            return "signup";
        }

        // TASK-BE-507: the account is created in the tenant of the client that sent the user
        // here (a web-store client → ecommerce), NOT in a hard-coded fan-platform. Resolved
        // from the saved /oauth2/authorize request — the same source the social path uses.
        // TASK-BE-581 re-resolves rather than threading the value down from the gate above:
        // the resolver is a pure read of the same session-held saved request, and keeping
        // one call site per concern is what stops the gate and the act from drifting apart.
        String tenantId = savedRequestTenantResolver.resolve(request, response).tenantId();

        try {
            accountServicePort.signup(normalizedEmail, password, normalizedDisplayName, tenantId);
            // Success → land on the login page with the success banner.
            return "redirect:/login?registered";
        } catch (SignupEmailConflictException e) {
            model.addAttribute("error", "이미 가입된 이메일입니다. 로그인해 주세요.");
            return "signup";
        } catch (SignupNotPossibleException e) {
            // TASK-BE-580: account-service refused for a reason retrying cannot change. This is
            // reachable even with TASK-BE-581's gate in front: the gate fails OPEN during an
            // account-service outage, and a tenant can be suspended between the check and the
            // call. The gate stops the common path; this stops the rest.
            log.info("Signup refused permanently: code={}", e.getErrorCode());
            model.addAttribute("signupBlocked", true);
            model.addAttribute("error", SIGNUP_NOT_AVAILABLE_MESSAGE);
            return "signup";
        } catch (SignupInvalidException e) {
            // TASK-BE-472: a 400/422 from account-service can be an email- OR password-format
            // problem, so name both — do not blame the password alone (the email pre-check above
            // already caught the common malformed-email case with a precise message).
            model.addAttribute("error",
                    "입력값을 확인해 주세요. 이메일 형식이 올바른지, 그리고 패스워드가 8자 이상이며 "
                            + "대문자·소문자·숫자·특수문자 중 3종 이상인지 확인해 주세요.");
            return "signup";
        } catch (AccountServiceUnavailableException e) {
            model.addAttribute("error", "잠시 후 다시 시도해 주세요. 인증 서비스가 일시적으로 불가합니다.");
            return "signup";
        } catch (RuntimeException e) {
            log.error("Unexpected signup proxy error", e);
            model.addAttribute("error", "회원가입 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
            return "signup";
        }
    }
}
