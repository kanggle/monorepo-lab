package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.AccountSecondFactorService;
import com.example.auth.infrastructure.security.SecondFactorSession;
import com.example.auth.infrastructure.security.SecondFactorSession.FirstFactor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * TASK-MONO-771 S2b (ADR-MONO-080 D4; auth-api.md § 로그인 흐름의 2단계 — 폼 · 소셜 공통) — the ONE place where a
 * signed-in IdP session is held back from {@code /oauth2/authorize} until it has passed the second step.
 *
 * <p>🔴 <b>Why here and not in a login producer (F6).</b> Two producers establish the session — the form login
 * ({@code CredentialAuthenticationProvider}) and the social callback ({@code SocialLoginBrowserController}). A
 * check in one of them leaves the other as a side door: an enrolled account would sign in through its social
 * identity and get a code with no second step. Both producers end at this endpoint, so the decision lives
 * after both of them — a session cannot reach a code by any first step without passing here.
 *
 * <p><b>The rule</b> (signed-in account session, {@code "mfa" ∉ amr}):
 * <ul>
 *   <li>account has a CONFIRMED enrollment → park this authorize, 302 {@code /mfa/challenge}
 *       («1단계만 통과» — no code is issued);</li>
 *   <li>no enrollment, {@code acr_values} has {@code mfa} (step-up) → park, 302 {@code /mfa/setup};</li>
 *   <li>no enrollment, no step-up → <b>untouched</b> — exactly today's flow (AC-3, OD-7);</li>
 *   <li>enrollment read fails → treated as enrolled (fail-closed): the challenge page re-reads and says
 *       «지금은 확인할 수 없습니다», nobody gets a code past an unreadable enrollment.</li>
 * </ul>
 * A session whose {@code amr} already holds {@code mfa}, an anonymous request and a non-account principal pass
 * untouched. A request that cannot be resumed by a redirect ({@code prompt=none}, a non-GET authorize) gets the
 * request's principal emptied instead — SAS then answers it as unauthenticated ({@code login_required} for
 * {@code prompt=none}), never with a code.
 *
 * <p>🔵 Not an enforcement of any POLICY: whether a token exchange / assume-tenant requires {@code mfa} is S4.
 * This gate only makes an enrolled account's own login honour its enrollment.
 *
 * <p>Runs right after {@link AuthorizeSessionTenantGate}: when that gate decides re-authentication it empties
 * the principal, and this one then has nothing to hold back.
 */
@Slf4j
final class AuthorizeSecondFactorGate extends OncePerRequestFilter {

    private final RequestMatcher authorizationEndpoint;
    private final AccountSecondFactorService secondFactorService;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    AuthorizeSecondFactorGate(String authorizationEndpointUri, AccountSecondFactorService secondFactorService) {
        this(authorizationEndpointUri, secondFactorService, SecurityContextHolder.getContextHolderStrategy());
    }

    AuthorizeSecondFactorGate(String authorizationEndpointUri, AccountSecondFactorService secondFactorService,
                              SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.authorizationEndpoint = new AntPathRequestMatcher(
                Objects.requireNonNull(authorizationEndpointUri, "authorizationEndpointUri"));
        this.secondFactorService = Objects.requireNonNull(secondFactorService, "secondFactorService");
        this.securityContextHolderStrategy = Objects.requireNonNull(
                securityContextHolderStrategy, "securityContextHolderStrategy");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !authorizationEndpoint.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication principal = securityContextHolderStrategy.getContext().getAuthentication();
        Optional<String> target = holdBackTarget(SecondFactorSession.of(principal),
                SecondFactorSession.requestsStepUp(request));
        if (target.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod()) || isPromptNone(request)) {
            // Cannot show a page / resume by redirect: answer as unauthenticated — never with a code.
            // Replace, never mutate (the holder's context IS the session's — TASK-BE-605's lesson).
            securityContextHolderStrategy.setContext(securityContextHolderStrategy.createEmptyContext());
            filterChain.doFilter(request, response);
            return;
        }
        SecondFactorSession.park(request, response);
        response.sendRedirect(request.getContextPath() + target.get());
    }

    /** Where this session must go before a code, or empty when it may pass. */
    Optional<String> holdBackTarget(Optional<FirstFactor> session, boolean stepUp) {
        if (session.isEmpty() || session.get().passedSecondStep()) {
            return Optional.empty();
        }
        boolean enrolled;
        try {
            enrolled = secondFactorService.hasConfirmedEnrollment(session.get().accountId());
        } catch (RuntimeException e) {
            log.warn("authorize: second-factor enrollment could not be read — held at the challenge "
                    + "(fail-closed): {}", e.getClass().getSimpleName());
            enrolled = true;
        }
        if (enrolled) {
            return Optional.of(SecondFactorSession.CHALLENGE_PATH);
        }
        return stepUp ? Optional.of(SecondFactorSession.SETUP_PATH) : Optional.empty();
    }

    private static boolean isPromptNone(HttpServletRequest request) {
        String prompt = request.getParameter("prompt");
        if (prompt == null) {
            return false;
        }
        for (String value : prompt.trim().split("\\s+")) {
            if ("none".equals(value)) {
                return true;
            }
        }
        return false;
    }
}
