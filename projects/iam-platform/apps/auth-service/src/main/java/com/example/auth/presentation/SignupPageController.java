package com.example.auth.presentation;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.SignupEmailConflictException;
import com.example.auth.application.exception.SignupInvalidException;
import com.example.auth.application.port.AccountServicePort;
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
    public String signupPage() {
        return "signup";
    }

    @PostMapping("/signup")
    public String submitSignup(
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "displayName", required = false) String displayName,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
            Model model) {

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

        try {
            accountServicePort.signup(normalizedEmail, password, normalizedDisplayName);
            // Success → land on the login page with the success banner.
            return "redirect:/login?registered";
        } catch (SignupEmailConflictException e) {
            model.addAttribute("error", "이미 가입된 이메일입니다. 로그인해 주세요.");
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
