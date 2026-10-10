package com.example.auth.presentation;

import com.example.auth.application.port.ConsumerPoolSignupPort;
import com.example.auth.application.port.OperatorInvitationAcceptancePort;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.AcceptResult;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.Preview;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.PreviewOutcome;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.PreviewResult;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.infrastructure.security.LoginBranding;
import com.example.auth.infrastructure.security.OperatorInvitationContinuation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * TASK-MONO-772 S3 (ADR-MONO-080 D6 · implementer decision D-4 · owner decision OD-3; auth-api.md § IdP 브라우저
 * 화면 — 운영자 초대 수락) — where the operator-invitation mail's link lands.
 *
 * <ul>
 *   <li>{@code GET /operator-invitations/accept?token=} — 🔴 changes nothing (mail scanners and link previews
 *       fetch links first, the {@code /verify-email} reason). Draws the invitation from admin's preview and, by the
 *       IdP session: no session → «log in» (the page is parked as the login continuation) + «create an IAM
 *       account»; a session that is not a pool account → «only a personal (IAM) account can accept» (admin is not
 *       called); a pool session → the «accept» form.</li>
 *   <li>{@code POST /operator-invitations/accept} — 🔴 the {@code accountId} is the <b>session principal's</b>; the
 *       form carries only the token. admin-service judges (verified owner · basis · OD-1) and writes.</li>
 *   <li>{@code GET · POST /operator-invitations/signup} — the site-less pool signup (OD-3): a pool account only,
 *       no store/fan membership. On success the acceptance page is parked as the login continuation and the
 *       browser goes to {@code /login?registered}.</li>
 * </ul>
 *
 * <p>On the {@code @Order(0)} form chain (same session as {@code /oauth2/authorize}, CSRF on the POSTs, permitAll —
 * the checks are here), like {@code /consent} and {@code /email-verification}.
 *
 * <p>🔴 R4: the token is rendered only into the forms' hidden fields (and the signup link's query, which is the link
 * the person already holds) — never as text, never in a log line. The invited address is shown masked only.
 */
@Slf4j
@Controller
public class OperatorInvitationPageController {

    static final String ACCEPT_VIEW = "operator-invitation-accept";
    static final String SIGNUP_VIEW = "operator-invitation-signup";

    /** {@code SignupPageController}'s pre-check, byte-identical (account-service's {@code Email} pattern). */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private final OperatorInvitationAcceptancePort acceptancePort;
    private final ConsumerPoolSignupPort poolSignupPort;
    private final String consoleUrl;

    public OperatorInvitationPageController(
            OperatorInvitationAcceptancePort acceptancePort,
            ConsumerPoolSignupPort poolSignupPort,
            @Value("${iam.operator-invitation.console-url:http://localhost:3000}") String consoleUrl) {
        this.acceptancePort = acceptancePort;
        this.poolSignupPort = poolSignupPort;
        this.consoleUrl = consoleUrl;
    }

    // ── accept ──────────────────────────────────────────────────────────────

    @GetMapping(OperatorInvitationContinuation.ACCEPT_PATH)
    public ModelAndView acceptPage(@RequestParam(name = "token", required = false) String token,
                                   Principal principal, HttpServletRequest request) {
        if (blank(token)) {
            return acceptView("not_found", null, null, HttpStatus.NOT_FOUND);
        }
        PreviewResult preview = acceptancePort.preview(token);
        if (preview.outcome() == PreviewOutcome.NOT_FOUND) {
            return acceptView("not_found", null, null, HttpStatus.NOT_FOUND);
        }
        if (preview.outcome() == PreviewOutcome.UNAVAILABLE) {
            return acceptView("preview_unavailable", null, null, HttpStatus.SERVICE_UNAVAILABLE);
        }
        Preview p = preview.preview();
        if ("ACCEPTED".equals(p.status())) {
            return acceptView("already_accepted", p, null, HttpStatus.OK);
        }
        if (p.expired()) {
            return acceptView("expired", p, null, HttpStatus.GONE);
        }

        EmailVerificationPageController.SessionAccount account =
                EmailVerificationPageController.sessionAccount(principal);
        if (account == null) {
            // «로그인» comes back here: park this page as the login continuation (the page is permitAll, so no entry
            // point does it). The form login then picks the consumer-pool credential only (S1-7).
            OperatorInvitationContinuation.park(request, token);
            return acceptView("login_required", p, token, HttpStatus.OK);
        }
        if (!TenantContext.isConsumerPool(account.tenantId())) {
            return acceptView("not_pool", p, null, HttpStatus.OK);
        }
        return acceptView("confirm", p, token, HttpStatus.OK);
    }

    @PostMapping(OperatorInvitationContinuation.ACCEPT_PATH)
    public ModelAndView accept(@RequestParam(name = "token", required = false) String token,
                               Principal principal, HttpServletRequest request) {
        if (blank(token)) {
            return acceptView("not_found", null, null, HttpStatus.NOT_FOUND);
        }
        EmailVerificationPageController.SessionAccount account =
                EmailVerificationPageController.sessionAccount(principal);
        if (account == null) {
            // The session ended between the page and the click — back to the login, then here.
            OperatorInvitationContinuation.park(request, token);
            return acceptView("login_required", previewOrNull(token), token, HttpStatus.UNAUTHORIZED);
        }
        if (!TenantContext.isConsumerPool(account.tenantId())) {
            return acceptView("not_pool", previewOrNull(token), null, HttpStatus.FORBIDDEN);
        }

        // 🔴 the account is the session's — never a form value.
        AcceptResult result = acceptancePort.accept(token, account.accountId());
        Preview p = previewOrNull(token);
        return switch (result.outcome()) {
            case ACCEPTED -> acceptView("accepted", p, null, HttpStatus.OK);
            case ALREADY_ACCEPTED -> acceptView("accepted_already_by_you", p, null, HttpStatus.OK);
            case EMAIL_NOT_VERIFIED -> acceptView("email_not_verified", p, null, HttpStatus.FORBIDDEN);
            case EMAIL_MISMATCH -> acceptView("email_mismatch", p, null, HttpStatus.FORBIDDEN);
            case ACCOUNT_NOT_ELIGIBLE -> acceptView("not_pool", p, null, HttpStatus.FORBIDDEN);
            case NOT_FOUND -> acceptView("not_found", null, null, HttpStatus.NOT_FOUND);
            case ALREADY_USED -> acceptView("already_used", p, null, HttpStatus.CONFLICT);
            case ALREADY_PROVISIONED -> acceptView("already_provisioned", p, null, HttpStatus.CONFLICT);
            case EMAIL_CONFLICT -> acceptView("email_conflict", p, null, HttpStatus.CONFLICT);
            case INVALIDATED -> acceptView("invalidated", p, null, HttpStatus.CONFLICT);
            case EXPIRED -> acceptView("expired", p, null, HttpStatus.GONE);
            // Nothing was written — the same link works again; keep the button.
            case UNAVAILABLE -> acceptView("accept_unavailable", p, token, HttpStatus.SERVICE_UNAVAILABLE);
        };
    }

    // ── signup ──────────────────────────────────────────────────────────────

    @GetMapping("/operator-invitations/signup")
    public ModelAndView signupPage(@RequestParam(name = "token", required = false) String token,
                                   HttpServletRequest request) {
        if (blank(token)) {
            return acceptView("not_found", null, null, HttpStatus.NOT_FOUND);
        }
        // The page's «이미 IAM 계정이 있으신가요? 로그인» must come back to the invitation too.
        OperatorInvitationContinuation.park(request, token);
        return signupView(token, null, null, null, HttpStatus.OK);
    }

    @PostMapping("/operator-invitations/signup")
    public ModelAndView signup(@RequestParam(name = "token", required = false) String token,
                               @RequestParam(name = "email", required = false) String email,
                               @RequestParam(name = "displayName", required = false) String displayName,
                               @RequestParam(name = "password", required = false) String password,
                               @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                               HttpServletRequest request) {
        if (blank(token)) {
            return acceptView("not_found", null, null, HttpStatus.NOT_FOUND);
        }
        String normalizedEmail = email == null ? "" : email.trim();
        String normalizedName = displayName == null ? "" : displayName.trim();

        // The consumer /signup page's checks and wording (SignupPageController) — the server is the authority.
        if (normalizedEmail.isEmpty() || password == null || password.isEmpty()) {
            return signupView(token, normalizedEmail, normalizedName, "이메일과 비밀번호를 입력해 주세요.", HttpStatus.OK);
        }
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return signupView(token, normalizedEmail, normalizedName, "이메일 형식이 올바르지 않습니다.", HttpStatus.OK);
        }
        if (password.length() < 8) {
            return signupView(token, normalizedEmail, normalizedName, "비밀번호는 8자 이상이어야 합니다.", HttpStatus.OK);
        }
        if (!password.equals(confirmPassword)) {
            return signupView(token, normalizedEmail, normalizedName, "비밀번호가 일치하지 않습니다.", HttpStatus.OK);
        }

        ConsumerPoolSignupPort.Outcome outcome = poolSignupPort.signup(normalizedEmail, password, normalizedName);
        return switch (outcome) {
            case CREATED -> {
                // Log in, then straight back to the acceptance page (which will say «verify your email first»).
                OperatorInvitationContinuation.park(request, token);
                yield new ModelAndView("redirect:/login?registered");
            }
            case ALREADY_EXISTS -> {
                // Same answer for «a pool account exists» and «a store/fan account uses this address» (§ 2 — the
                // two are one code at account-service, deliberately). The login comes back to the invitation.
                OperatorInvitationContinuation.park(request, token);
                yield signupView(token, normalizedEmail, normalizedName,
                        "이미 사용 중인 주소입니다. IAM 계정이 있으면 로그인하세요. 스토어 · 팬 계정으로 쓰이는 주소라면 "
                                + "그 계정으로 로그인한 브라우저에서 초대 링크를 다시 여세요.",
                        HttpStatus.CONFLICT);
            }
            case INVALID -> signupView(token, normalizedEmail, normalizedName,
                    "입력값을 확인해 주세요. 이메일 형식이 올바른지, 그리고 비밀번호가 8자 이상이며 "
                            + "대문자·소문자·숫자·특수문자 중 3종 이상인지 확인해 주세요.", HttpStatus.UNPROCESSABLE_ENTITY);
            case NOT_POSSIBLE, UNAVAILABLE -> signupView(token, normalizedEmail, normalizedName,
                    "지금은 가입할 수 없습니다. 잠시 뒤 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);
        };
    }

    // ── views ───────────────────────────────────────────────────────────────

    private Preview previewOrNull(String token) {
        PreviewResult preview = acceptancePort.preview(token);
        return preview.outcome() == PreviewOutcome.FOUND ? preview.preview() : null;
    }

    private ModelAndView acceptView(String state, Preview preview, String token, HttpStatus status) {
        ModelAndView mav = new ModelAndView(ACCEPT_VIEW, status);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("state", state);
        mav.addObject("company", preview == null ? null : preview.companyName());
        mav.addObject("maskedEmail", preview == null ? null : preview.maskedEmail());
        mav.addObject("roles", preview == null ? List.of() : preview.roles());
        mav.addObject("token", token);
        mav.addObject("consoleUrl", consoleUrl);
        return mav;
    }

    private static ModelAndView signupView(String token, String email, String displayName, String error,
                                           HttpStatus status) {
        ModelAndView mav = new ModelAndView(SIGNUP_VIEW, status);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("token", token);
        mav.addObject("email", email);
        mav.addObject("displayName", displayName);
        mav.addObject("error", error);
        return mav;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
