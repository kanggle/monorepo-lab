package com.example.auth.presentation;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.AccountServicePort.EmailVerificationConfirmOutcome;
import com.example.auth.application.port.AccountServicePort.EmailVerificationRequestOutcome;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.infrastructure.security.LoginBranding;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.Map;

/**
 * TASK-MONO-770 (ADR-MONO-080 D3) — the two IdP pages of email verification (auth-api.md § IdP 브라우저 화면 —
 * 이메일 인증).
 *
 * <ul>
 *   <li>{@code /email-verification} — <b>send</b> the mail. Needs the IdP session (the account is whoever is
 *       signed in to the IdP in this browser); without one the page says so and calls nothing.</li>
 *   <li>{@code /verify-email} — where the mail's link <b>lands</b>. GET only draws a button; POST consumes the
 *       token. 🔴 GET must not verify: mail scanners and link previews fetch links before the person does, and a
 *       side-effecting GET would spend the token before anyone opened it. No session needed — the link may be
 *       opened on another device; the token is the credential.</li>
 * </ul>
 *
 * <p>Both call account-service server-side (same reason as {@code /signup}: the IdP pages and
 * {@code /api/accounts} are different origins, and account-service sets no CORS). Both live on the
 * {@code @Order(0)} form chain (CSRF on, permitAll — the checks are here), like {@code /consent}.
 *
 * <p>🔴 The send-failure answer is the point of ADR-MONO-080 § 새로 생기는 위험: a failed send is shown as «메일을
 * 보내지 못했습니다 · 다시 시도», never as something that reads like «you were not given the role».
 *
 * <p>R4: the token is never logged or rendered as text (only back into the confirm form's hidden field, to the
 * person who opened the link); the address is shown masked.
 */
@Controller
@RequiredArgsConstructor
public class EmailVerificationPageController {

    static final String SEND_VIEW = "email-verification";
    static final String CONFIRM_VIEW = "verify-email";

    private final AccountServicePort accountServicePort;

    @GetMapping("/email-verification")
    public ModelAndView sendPage(Principal principal) {
        SessionAccount account = sessionAccount(principal);
        if (account == null) {
            return sendView("no_session", null, HttpStatus.OK);
        }
        return sendView(null, account.maskedEmail(), HttpStatus.OK);
    }

    @PostMapping("/email-verification")
    public ModelAndView send(Principal principal) {
        SessionAccount account = sessionAccount(principal);
        if (account == null) {
            return sendView("no_session", null, HttpStatus.UNAUTHORIZED);
        }
        EmailVerificationRequestOutcome outcome =
                accountServicePort.requestVerificationEmail(account.accountId(), account.tenantId());
        HttpStatus status = switch (outcome) {
            case SENT, ALREADY_VERIFIED -> HttpStatus.OK;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNDELIVERABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case NOT_APPLICABLE -> HttpStatus.NOT_FOUND;
            case SEND_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return sendView(outcome.name(), account.maskedEmail(), status);
    }

    @GetMapping("/verify-email")
    public ModelAndView confirmPage(@RequestParam(name = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return confirmView("INVALID_OR_EXPIRED", null, HttpStatus.BAD_REQUEST);
        }
        return confirmView(null, token, HttpStatus.OK);
    }

    @PostMapping("/verify-email")
    public ModelAndView confirm(@RequestParam(name = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return confirmView("INVALID_OR_EXPIRED", null, HttpStatus.BAD_REQUEST);
        }
        EmailVerificationConfirmOutcome outcome = accountServicePort.confirmEmailVerification(token);
        return switch (outcome) {
            case VERIFIED, ALREADY_VERIFIED -> confirmView(outcome.name(), null, HttpStatus.OK);
            case INVALID_OR_EXPIRED -> confirmView(outcome.name(), null, HttpStatus.BAD_REQUEST);
            // The token was not consumed — keep the button so the same link works on the next try.
            case UNAVAILABLE -> confirmView(outcome.name(), token, HttpStatus.SERVICE_UNAVAILABLE);
        };
    }

    private static ModelAndView sendView(String result, String maskedEmail, HttpStatus status) {
        ModelAndView mav = new ModelAndView(SEND_VIEW, status);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("result", result);
        mav.addObject("maskedEmail", maskedEmail);
        return mav;
    }

    private static ModelAndView confirmView(String result, String token, HttpStatus status) {
        ModelAndView mav = new ModelAndView(CONFIRM_VIEW, status);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("result", result);
        mav.addObject("token", token);
        return mav;
    }

    /** The signed-in account of this IdP session, or {@code null} (anonymous / no account id in the session). */
    static SessionAccount sessionAccount(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            return null;
        }
        if (!(details.get(PrincipalDetailKeys.ACCOUNT_ID) instanceof String accountId) || accountId.isBlank()) {
            return null;
        }
        String tenantId = details.get(PrincipalDetailKeys.TENANT_ID) instanceof String t && !t.isBlank() ? t : null;
        String email = details.get(PrincipalDetailKeys.EMAIL) instanceof String e && !e.isBlank() ? e : null;
        return new SessionAccount(accountId, tenantId, mask(email));
    }

    /** Same format as the senders' log masking ({@code a***@example.com}); {@code null} when there is none. */
    static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return null;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    record SessionAccount(String accountId, String tenantId, String maskedEmail) {
    }
}
