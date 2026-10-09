package com.example.auth.presentation;

import com.example.auth.application.AccountSecondFactorService;
import com.example.auth.application.AccountSecondFactorService.ConfirmEnrollmentResult;
import com.example.auth.application.AccountSecondFactorService.EnrollmentStatus;
import com.example.auth.application.AccountSecondFactorService.RecoveryOutcome;
import com.example.auth.application.AccountSecondFactorService.StartEnrollmentResult;
import com.example.auth.application.AccountSecondFactorService.VerificationOutcome;
import com.example.auth.domain.session.AuthenticationMethods;
import com.example.auth.infrastructure.security.LoginBranding;
import com.example.auth.infrastructure.security.PendingSiteConsentStore;
import com.example.auth.infrastructure.security.SecondFactorSession;
import com.example.auth.infrastructure.security.SecondFactorSession.FirstFactor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * TASK-MONO-771 S2b (ADR-MONO-080 D4 · R3) — the IdP second-factor pages (auth-api.md § IdP 브라우저 화면 — 2단계
 * 인증 (TOTP)): {@code /mfa/challenge} (the second step, shared by form AND social login — F6),
 * {@code /mfa/setup} (enrollment, OD-4 preconditions), {@code /mfa} (status) and {@code /mfa/recovery-codes}.
 *
 * <p>On the {@code @Order(0)} form chain (CSRF on the POSTs, permitAll — every check is here). The session's
 * first-step principal and the parked authorize come from {@link SecondFactorSession}; the authorize gate
 * ({@code AuthorizeSecondFactorGate}) is what sends a session here.
 *
 * <p>🔴 Nothing here refuses a token: an account WITHOUT a confirmed enrollment never reaches these pages from a
 * login (AC-3), and policy enforcement (operator exchange / assume-tenant) is S4.
 *
 * <p>R4: the secret, the otpauth URI and recovery codes appear in the response body only — never in a log line.
 */
@Slf4j
@Controller
public class MfaPageController {

    static final String CHALLENGE_VIEW = "mfa-challenge";
    static final String SETUP_VIEW = "mfa-setup";
    static final String STATUS_VIEW = "mfa";

    /** Fixed {@code error_description} of a cancelled second step (auth-api.md § /mfa/challenge «취소»). */
    static final String CANCELLED_DESCRIPTION = "mfa_cancelled";

    /** «남은 복구 코드가 2개 이하면 재발급 안내». */
    static final int LOW_RECOVERY_CODES = 2;

    private final AccountSecondFactorService service;
    private final RegisteredClientRepository registeredClientRepository;
    private final String issuer;

    public MfaPageController(AccountSecondFactorService service,
                             RegisteredClientRepository registeredClientRepository,
                             @Value("${auth.totp.issuer:IAM}") String issuer) {
        this.service = service;
        this.registeredClientRepository = registeredClientRepository;
        this.issuer = issuer;
    }

    // ------------------------------------------------------------------ /mfa/challenge

    @GetMapping("/mfa/challenge")
    public ModelAndView challengePage(Principal principal, HttpServletRequest request, HttpServletResponse response) {
        Optional<FirstFactor> session = firstFactor(principal);
        if (session.isEmpty()) {
            return redirect("/login");
        }
        if (session.get().passedSecondStep()) {
            return redirect(SecondFactorSession.resumeTarget(request, response));
        }
        try {
            if (!service.hasConfirmedEnrollment(session.get().accountId())) {
                // Nothing to challenge (e.g. reset meanwhile) — the gate lets this session through as before.
                return redirect(SecondFactorSession.resumeTarget(request, response));
            }
        } catch (RuntimeException e) {
            return challengeView("UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return challengeView(null, HttpStatus.OK);
    }

    @PostMapping("/mfa/challenge")
    public ModelAndView challenge(Principal principal,
                                  @RequestParam(name = "code", required = false) String code,
                                  @RequestParam(name = "recoveryCode", required = false) String recoveryCode,
                                  @RequestParam(name = "action", required = false) String action,
                                  HttpServletRequest request, HttpServletResponse response) {
        if ("cancel".equals(action)) {
            return cancel(request, response);
        }
        Optional<FirstFactor> session = firstFactor(principal);
        if (session.isEmpty()) {
            return redirect("/login");
        }
        FirstFactor first = session.get();
        if (first.passedSecondStep()) {
            return redirect(SecondFactorSession.resumeTarget(request, response));
        }
        Authentication current = (Authentication) principal;
        try {
            if (recoveryCode != null && !recoveryCode.isBlank()) {
                RecoveryOutcome outcome = service.verifyRecoveryCode(first.accountId(), recoveryCode);
                if (outcome.accepted()) {
                    SecondFactorSession.upgrade(current,
                            AuthenticationMethods.withRecoveryCode(first.firstStepMethods()), request, response);
                    String next = SecondFactorSession.resumeTarget(request, response);
                    if (outcome.remaining() <= LOW_RECOVERY_CODES) {
                        ModelAndView mav = challengeView("RECOVERY_LOW", HttpStatus.OK);
                        mav.addObject("remaining", outcome.remaining());
                        mav.addObject("continueUrl", next);
                        return mav;
                    }
                    return redirect(next);
                }
            } else {
                VerificationOutcome outcome = service.verifyAuthenticatorCode(first.accountId(), code);
                if (outcome == VerificationOutcome.ACCEPTED) {
                    SecondFactorSession.upgrade(current,
                            AuthenticationMethods.withAuthenticatorCode(first.firstStepMethods()), request, response);
                    return redirect(SecondFactorSession.resumeTarget(request, response));
                }
                if (outcome == VerificationOutcome.NOT_ENROLLED) {
                    return redirect(SecondFactorSession.resumeTarget(request, response));
                }
            }
        } catch (RuntimeException e) {
            log.warn("mfa challenge: enrollment could not be read — not passed (fail-closed): {}",
                    e.getClass().getSimpleName());
            return challengeView("UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
        // Wrong code, replayed code and malformed input look the same (auth-api.md).
        if (SecondFactorSession.recordFailure(request) >= SecondFactorSession.MAX_FAILURES) {
            SecondFactorSession.dropFirstFactor(request, response);
            return redirect("/login");
        }
        return challengeView("WRONG_CODE", HttpStatus.OK);
    }

    // ------------------------------------------------------------------ /mfa/setup

    @GetMapping("/mfa/setup")
    public ModelAndView setupPage(Principal principal) {
        Optional<FirstFactor> session = firstFactor(principal);
        if (session.isEmpty()) {
            return redirect("/login");
        }
        StartEnrollmentResult started;
        try {
            started = service.startEnrollment(session.get().accountId(),
                    tenantOf(session.get()));
        } catch (RuntimeException e) {
            return setupView("UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return switch (started.outcome()) {
            case ALREADY_ENROLLED -> setupView("ALREADY_ENROLLED", HttpStatus.OK);
            case EMAIL_NOT_VERIFIED -> setupView("EMAIL_NOT_VERIFIED", HttpStatus.FORBIDDEN);
            case UNAVAILABLE -> setupView("UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
            case PENDING_CREATED -> pendingView(null, started.base32Secret(), session.get(), HttpStatus.OK);
        };
    }

    @PostMapping("/mfa/setup")
    public ModelAndView setup(Principal principal,
                              @RequestParam(name = "code", required = false) String code,
                              @RequestParam(name = "action", required = false) String action,
                              HttpServletRequest request, HttpServletResponse response) {
        if ("cancel".equals(action)) {
            return cancel(request, response);
        }
        Optional<FirstFactor> session = firstFactor(principal);
        if (session.isEmpty()) {
            return redirect("/login");
        }
        FirstFactor first = session.get();
        ConfirmEnrollmentResult result;
        try {
            result = service.confirmEnrollment(first.accountId(), code, first.email());
        } catch (RuntimeException e) {
            return setupView("UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!result.confirmed()) {
            Optional<String> key;
            try {
                key = service.pendingManualKey(first.accountId());
            } catch (RuntimeException e) {
                key = Optional.empty();
            }
            // Same QR / key again when the pending secret is still alive; else back to GET for a new one.
            return key.map(k -> pendingView("WRONG_CODE", k, first, HttpStatus.OK))
                    .orElseGet(() -> setupView("PENDING_GONE", HttpStatus.OK));
        }
        // The first code from the app IS the second step (auth-api.md § 단계 상승 — 등록 없음).
        SecondFactorSession.upgrade((Authentication) principal,
                AuthenticationMethods.withAuthenticatorCode(first.firstStepMethods()), request, response);
        ModelAndView mav = setupView("CONFIRMED", HttpStatus.OK);
        mav.addObject("recoveryCodes", result.recoveryCodes());
        mav.addObject("continueUrl", SecondFactorSession.resumeTarget(request, response));
        return mav;
    }

    // ------------------------------------------------------------------ /mfa · /mfa/recovery-codes

    @GetMapping("/mfa")
    public ModelAndView statusPage(Principal principal) {
        Optional<FirstFactor> session = firstFactor(principal);
        if (session.isEmpty()) {
            return redirect("/login");
        }
        EnrollmentStatus status;
        try {
            status = service.status(session.get().accountId());
        } catch (RuntimeException e) {
            return statusView("UNAVAILABLE", null, HttpStatus.SERVICE_UNAVAILABLE);
        }
        return statusView(null, status, HttpStatus.OK);
    }

    @PostMapping("/mfa/recovery-codes")
    public ModelAndView regenerateRecoveryCodes(Principal principal) {
        Optional<FirstFactor> session = firstFactor(principal);
        if (session.isEmpty()) {
            return redirect("/login");
        }
        if (!session.get().passedSecondStep()) {
            // Regenerating is the right of a session that passed the second step (auth-api.md).
            return redirect(SecondFactorSession.CHALLENGE_PATH);
        }
        Optional<List<String>> codes;
        try {
            codes = service.regenerateRecoveryCodes(session.get().accountId());
        } catch (RuntimeException e) {
            return statusView("UNAVAILABLE", null, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (codes.isEmpty()) {
            return redirect(SecondFactorSession.STATUS_PATH);
        }
        ModelAndView mav = statusView("REGENERATED",
                new EnrollmentStatus(true, codes.get().size()), HttpStatus.OK);
        mav.addObject("recoveryCodes", codes.get());
        return mav;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * «취소» — the parked authorize is answered {@code access_denied} / {@code mfa_cancelled} on its client's
     * REGISTERED redirect URI (OIDC Core 3.1.2.6). Nothing parked, or a redirect URI that cannot be trusted →
     * {@code /login}.
     */
    private ModelAndView cancel(HttpServletRequest request, HttpServletResponse response) {
        Optional<SavedRequest> parked = SecondFactorSession.parkedAuthorize(request, response);
        SecondFactorSession.forgetParked(request, response);
        if (parked.isEmpty()) {
            return redirect("/login");
        }
        String clientId = first(parked.get(), "client_id");
        RegisteredClient client = clientId == null ? null : registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            return redirect("/login");
        }
        return PendingSiteConsentStore.errorRedirect(client, first(parked.get(), "redirect_uri"),
                        first(parked.get(), "state"), "access_denied", CANCELLED_DESCRIPTION)
                .map(MfaPageController::redirect)
                .orElseGet(() -> redirect("/login"));
    }

    private ModelAndView pendingView(String result, String base32Secret, FirstFactor session, HttpStatus status) {
        ModelAndView mav = setupView(result == null ? "PENDING" : result, status);
        mav.addObject("manualKey", groupKey(base32Secret));
        mav.addObject("otpauthUri", otpauthUri(base32Secret, session.email()));
        return mav;
    }

    /**
     * {@code otpauth://totp/<issuer>:<masked email>?secret=…&issuer=…&algorithm=SHA1&digits=6&period=30}. The
     * label shows the MASKED address (the contract's choice — the URI is rendered into the page).
     */
    String otpauthUri(String base32Secret, String email) {
        String masked = EmailVerificationPageController.mask(email);
        String label = issuer + ":" + (masked == null ? "account" : masked);
        return "otpauth://totp/" + UriUtils.encodePath(label, StandardCharsets.UTF_8)
                + "?secret=" + base32Secret
                + "&issuer=" + UriUtils.encodeQueryParam(issuer, StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String groupKey(String base32) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < base32.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                out.append(' ');
            }
            out.append(base32.charAt(i));
        }
        return out.toString();
    }

    private static Optional<FirstFactor> firstFactor(Principal principal) {
        return principal instanceof Authentication authentication
                ? SecondFactorSession.of(authentication)
                : Optional.empty();
    }

    /** The account's stored tenant for the M1 column — the session's detail tenant (pool or site). */
    private static String tenantOf(FirstFactor session) {
        return session.tenantId() != null ? session.tenantId() : "unknown";
    }

    private static String first(SavedRequest saved, String name) {
        String[] values = saved.getParameterValues(name);
        return values != null && values.length > 0 && values[0] != null && !values[0].isBlank() ? values[0] : null;
    }

    private static ModelAndView redirect(String target) {
        return new ModelAndView("redirect:" + target);
    }

    private static ModelAndView challengeView(String result, HttpStatus status) {
        ModelAndView mav = new ModelAndView(CHALLENGE_VIEW, status);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("result", result);
        return mav;
    }

    private static ModelAndView setupView(String result, HttpStatus status) {
        ModelAndView mav = new ModelAndView(SETUP_VIEW, status);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("result", result);
        return mav;
    }

    private static ModelAndView statusView(String result, EnrollmentStatus status, HttpStatus httpStatus) {
        ModelAndView mav = new ModelAndView(STATUS_VIEW, httpStatus);
        mav.addObject("branding", LoginBranding.DEFAULT);
        mav.addObject("result", result);
        mav.addObject("enrolled", status != null && status.enrolled());
        mav.addObject("remaining", status == null ? 0 : status.remainingRecoveryCodes());
        return mav;
    }
}
