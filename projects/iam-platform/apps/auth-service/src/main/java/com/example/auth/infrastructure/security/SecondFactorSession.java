package com.example.auth.infrastructure.security;

import com.example.auth.domain.session.AuthenticationMethods;
import com.example.auth.domain.session.PrincipalDetailKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TASK-MONO-771 S2b — the IdP browser session as the second step sees it (auth-api.md § IdP 브라우저 화면 — 2단계
 * 인증). One place for the four things the authorize gate and the {@code /mfa/**} pages must agree on:
 *
 * <ol>
 *   <li><b>Who passed the first step</b> — an authenticated session principal whose {@code details} carry an
 *       {@code account_id} (both producers — form and social — write the same shape).</li>
 *   <li><b>How</b> — {@code details.amr}; «second step passed» ⇔ {@code "mfa" ∈ amr} (the contract's only
 *       predicate).</li>
 *   <li><b>Where to resume</b> — the parked {@code /oauth2/authorize} request. It lives in the STANDARD
 *       {@link HttpSessionRequestCache} on purpose (unlike {@link PendingSiteConsentStore}): it IS a login
 *       continuation. After five failed codes the first step is dropped and the next login must resume exactly
 *       this request — and the form login's scoped credential lookup and the social path's tenant both read the
 *       initiating client from that same cache ({@link SavedRequestTenantResolver}).</li>
 *   <li><b>How many codes failed</b> in this first-step session (cap {@link #MAX_FAILURES}, then back to
 *       {@code /login} — no account lock, BE-599's reason).</li>
 * </ol>
 */
public final class SecondFactorSession {

    /** {@code /mfa/challenge} — the shared second step after form AND social login (F6). */
    public static final String CHALLENGE_PATH = "/mfa/challenge";

    /** {@code /mfa/setup} — enrollment; the step-up target for a session with no enrollment. */
    public static final String SETUP_PATH = "/mfa/setup";

    /** {@code /mfa} — status. */
    public static final String STATUS_PATH = "/mfa";

    /** Failed codes per first-step session before the first step is dropped (auth-api.md: 5). */
    public static final int MAX_FAILURES = 5;

    static final String FAILURES_ATTRIBUTE = "IAM_MFA_FAILED_ATTEMPTS";

    private static final String AUTHORIZE_PATH = "/oauth2/authorize";

    private static final RequestCache REQUEST_CACHE = new HttpSessionRequestCache();
    private static final SecurityContextRepository CONTEXT_REPOSITORY = new HttpSessionSecurityContextRepository();

    private SecondFactorSession() {
    }

    /** The first-step facts of {@code authentication}, or empty when it is not a signed-in account session. */
    public static Optional<FirstFactor> of(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            return Optional.empty();
        }
        if (!(details.get(PrincipalDetailKeys.ACCOUNT_ID) instanceof String accountId) || accountId.isBlank()) {
            return Optional.empty();
        }
        String tenantId = details.get(PrincipalDetailKeys.TENANT_ID) instanceof String t && !t.isBlank() ? t : null;
        String email = details.get(PrincipalDetailKeys.EMAIL) instanceof String e && !e.isBlank() ? e : null;
        List<String> amr = AuthenticationMethods.read(details.get(PrincipalDetailKeys.AMR));
        return Optional.of(new FirstFactor(accountId, tenantId, email, amr));
    }

    /** {@code acr_values} contains the token {@code mfa} (RFC 9470 step-up; any other value is ignored). */
    public static boolean requestsStepUp(HttpServletRequest request) {
        String acrValues = request.getParameter("acr_values");
        if (acrValues == null) {
            return false;
        }
        for (String value : acrValues.trim().split("\\s+")) {
            if (AuthenticationMethods.MFA.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** Parks the current authorize request as the login continuation (see the class note, item 3). */
    public static void park(HttpServletRequest request, HttpServletResponse response) {
        REQUEST_CACHE.saveRequest(request, response);
    }

    /** The parked {@code /oauth2/authorize} request, when there is one. */
    public static Optional<SavedRequest> parkedAuthorize(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = REQUEST_CACHE.getRequest(request, response);
        if (saved == null || saved.getRedirectUrl() == null || !saved.getRedirectUrl().contains(AUTHORIZE_PATH)) {
            return Optional.empty();
        }
        return Optional.of(saved);
    }

    /** Where to go once the second step is done: the parked authorize, else {@code /mfa}. */
    public static String resumeTarget(HttpServletRequest request, HttpServletResponse response) {
        return parkedAuthorize(request, response).map(SavedRequest::getRedirectUrl).orElse(STATUS_PATH);
    }

    /** Forgets the parked authorize (the person cancelled). */
    public static void forgetParked(HttpServletRequest request, HttpServletResponse response) {
        REQUEST_CACHE.removeRequest(request, response);
    }

    /**
     * The second step passed: the session principal is REPLACED by one whose {@code details.amr} is
     * {@code newAmr} (everything else copied), the session id rotates (privilege change — the same defence
     * TASK-BE-521 applies at social login), and the failure counter resets. Only tokens minted AFTER this carry
     * the new value; the parked authorize resumes and SAS stores this principal on the new authorization, so a
     * later refresh keeps it.
     */
    public static void upgrade(Authentication current, List<String> newAmr,
                               HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> details = new HashMap<>();
        if (current.getDetails() instanceof Map<?, ?> existing) {
            existing.forEach((k, v) -> details.put(String.valueOf(k), v));
        }
        details.put(PrincipalDetailKeys.AMR, new ArrayList<>(newAmr)); // allowlist: mutable ArrayList
        UsernamePasswordAuthenticationToken upgraded = UsernamePasswordAuthenticationToken.authenticated(
                current.getPrincipal(), null, current.getAuthorities());
        upgraded.setDetails(details);

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(upgraded);
        SecurityContextHolder.setContext(context);
        CONTEXT_REPOSITORY.saveContext(context, request, response);
        resetFailures(request);
    }

    /**
     * Five failed codes: the first step is dropped (the session no longer holds a signed-in principal) while the
     * parked authorize stays, so the person starts again from the password / provider and lands back here.
     */
    public static void dropFirstFactor(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        CONTEXT_REPOSITORY.saveContext(empty, request, response);
        resetFailures(request);
    }

    /** Counts one failed code; returns the count so far in this first-step session. */
    public static int recordFailure(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        int count = session.getAttribute(FAILURES_ATTRIBUTE) instanceof Integer n ? n + 1 : 1;
        session.setAttribute(FAILURES_ATTRIBUTE, count);
        return count;
    }

    private static void resetFailures(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(FAILURES_ATTRIBUTE);
        }
    }

    /**
     * The first-step facts of a session.
     *
     * @param amr the session's methods, {@code null} for a session established before this change
     */
    public record FirstFactor(String accountId, String tenantId, String email, List<String> amr) {

        public boolean passedSecondStep() {
            return AuthenticationMethods.hasSecondFactor(amr);
        }

        /** The first-step methods to build on ({@code []} for a pre-change session with no value). */
        public List<String> firstStepMethods() {
            return amr == null ? List.of() : amr;
        }
    }
}
