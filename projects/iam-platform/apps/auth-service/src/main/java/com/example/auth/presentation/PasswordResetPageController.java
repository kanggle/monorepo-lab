package com.example.auth.presentation;

import com.example.auth.application.ConfirmPasswordResetUseCase;
import com.example.auth.application.RequestPasswordResetUseCase;
import com.example.auth.application.command.ConfirmPasswordResetCommand;
import com.example.auth.application.command.RequestPasswordResetCommand;
import com.example.auth.application.exception.PasswordResetTokenInvalidException;
import com.example.auth.domain.credentials.PasswordPolicyViolationException;
import com.example.auth.infrastructure.security.LoginBranding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * TASK-BE-627 (TASK-MONO-770 의 남은 화면, ADR-MONO-080 D3) — the two IdP pages of password
 * reset (auth-api.md § IdP 브라우저 화면 — 비밀번호 재설정).
 *
 * <ul>
 *   <li>{@code /password-reset/request} — send the reset mail. 🔴 AC-1 (existence non-disclosure):
 *       {@link RequestPasswordResetUseCase#execute} never tells the caller whether the email
 *       existed or the per-email rate limit was hit — both are silent no-ops absorbed inside the
 *       use case (see its Javadoc), so {@code execute()} returns normally for the existing-email,
 *       unknown-email AND rate-limited cases alike. This controller has no branch that could even
 *       distinguish them — do not add one "to improve the message": that would re-introduce the
 *       account-existence leak the use case exists to prevent.</li>
 *   <li>{@code /password-reset} — where the mail's link lands ({@code iam.mail.password-reset-link-base-url},
 *       default {@code http://localhost:8081/password-reset}). GET renders the new-password form
 *       when a token is present, or a "request one" notice when it is not (Edge Case: a bare visit
 *       is guidance, not an error). POST consumes the token via
 *       {@link ConfirmPasswordResetUseCase}. Neither verb looks at the IdP session — the Edge Case
 *       "로그인 상태에서 링크를 연다" needs no special handling because the token alone authorizes
 *       the change.</li>
 * </ul>
 *
 * <p>Both live on the {@code @Order(0)} form chain ({@code WebLoginSecurityConfig}) — CSRF on,
 * permitAll, same as {@code /login} · {@code /signup} · {@code /consent} ·
 * {@code /email-verification} · {@code /verify-email}. Unlike
 * {@link EmailVerificationPageController} (which calls account-service over HTTP because
 * email-verification state lives there), both use cases here are auth-service-local application
 * services — same shape as the existing {@link PasswordResetController} JSON API — so this
 * controller calls them directly (same process, no HTTP hop).
 *
 * <p>R4: the token is never logged; it only ever travels in the confirm form's hidden field, back
 * to whoever opened the link. 🔴 Failure Scenario 3: a policy-violation re-render keeps the token
 * (so the user does not have to request a second mail) but never re-fills the password fields —
 * {@link #confirmView} does not carry a password value at all.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PasswordResetPageController {

    static final String REQUEST_VIEW = "password-reset-request";
    static final String CONFIRM_VIEW = "password-reset";

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    @GetMapping("/password-reset/request")
    public ModelAndView requestPage() {
        return requestView(null, null, HttpStatus.OK);
    }

    @PostMapping("/password-reset/request")
    public ModelAndView requestSubmit(@RequestParam(name = "email", required = false) String email) {
        String normalized = email == null ? "" : email.trim();
        if (normalized.isEmpty()) {
            // A required-field check applied before any account lookup — it cannot leak
            // account existence because no lookup has happened yet.
            return requestView("EMAIL_REQUIRED", null, HttpStatus.BAD_REQUEST);
        }
        try {
            // 🔴 AC-1 / AC-5: existing email, unknown email, and a rate-limited request all take
            // this single branch below — see class Javadoc. Do not special-case any of them here.
            requestPasswordResetUseCase.execute(new RequestPasswordResetCommand(normalized));
        } catch (RuntimeException e) {
            // Defensive only (e.g. a Redis outage — RequestPasswordResetUseCase's Javadoc notes
            // Redis failures propagate). Without this catch an uncaught exception here would be
            // intercepted by AuthExceptionHandler (a @RestControllerAdvice) and answer with a raw
            // JSON error body instead of this HTML page.
            log.error("Unexpected password-reset request error", e);
            return requestView("UNAVAILABLE", normalized, HttpStatus.SERVICE_UNAVAILABLE);
        }
        return requestView("SENT", null, HttpStatus.OK);
    }

    @GetMapping("/password-reset")
    public ModelAndView confirmPage(@RequestParam(name = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            // Edge Case: a bare /password-reset visit (no token) is guidance toward the request
            // page, not an error screen.
            return confirmView("NO_TOKEN", null, HttpStatus.OK);
        }
        return confirmView(null, token, HttpStatus.OK);
    }

    @PostMapping("/password-reset")
    public ModelAndView confirmSubmit(
            @RequestParam(name = "token", required = false) String token,
            @RequestParam(name = "newPassword", required = false) String newPassword,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword) {
        if (token == null || token.isBlank()) {
            return confirmView("NO_TOKEN", null, HttpStatus.OK);
        }
        if (newPassword == null || newPassword.isEmpty() || !newPassword.equals(confirmPassword)) {
            // Client-side also pre-checks this (password-reset.html); the server re-validates
            // (source of truth). This is a form mistake, not a used link — keep the token.
            return confirmView("PASSWORD_MISMATCH", token, HttpStatus.BAD_REQUEST);
        }
        try {
            confirmPasswordResetUseCase.execute(new ConfirmPasswordResetCommand(token, newPassword));
        } catch (PasswordResetTokenInvalidException e) {
            // Token unknown/expired/already-used — retrying with the SAME token cannot succeed,
            // so (unlike the policy-violation branch below) no token is carried back into the form.
            return confirmView("TOKEN_INVALID", null, HttpStatus.BAD_REQUEST);
        } catch (PasswordPolicyViolationException e) {
            // 🔴 Failure Scenario 3: keep the token — losing it here would force a second mail
            // round-trip for a mistake that has nothing to do with the token's validity.
            return confirmView("POLICY_VIOLATION", token, HttpStatus.BAD_REQUEST);
        } catch (RuntimeException e) {
            log.error("Unexpected password-reset confirm error", e);
            return confirmView("UNAVAILABLE", token, HttpStatus.SERVICE_UNAVAILABLE);
        }
        // Success → the login page, with a notice (TASK-BE-627 Scope: "성공 → 로그인 화면").
        return new ModelAndView("redirect:/login?passwordReset");
    }

    private static ModelAndView requestView(String result, String email, HttpStatus status) {
        ModelAndView mav = new ModelAndView(REQUEST_VIEW, status);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("result", result);
        mav.addObject("email", email);
        return mav;
    }

    private static ModelAndView confirmView(String result, String token, HttpStatus status) {
        ModelAndView mav = new ModelAndView(CONFIRM_VIEW, status);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("result", result);
        mav.addObject("token", token);
        return mav;
    }
}
