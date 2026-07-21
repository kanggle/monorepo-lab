package com.example.auth.presentation.exception;

import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.exception.CredentialsInvalidException;
import com.example.auth.application.exception.CurrentPasswordMismatchException;
import com.example.auth.application.exception.InvalidOAuthStateException;
import com.example.auth.application.exception.LoginRateLimitedException;
import com.example.auth.application.exception.OAuthCodeInvalidException;
import com.example.auth.application.exception.OAuthProviderException;
import com.example.web.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the account-status branch of {@link AuthExceptionHandler}. Invokes
 * the handler directly — the mapping is a pure {@code switch}, so Spring MVC buys
 * nothing.
 *
 * <p>Pins the {@code (code, HTTP status)} contract of
 * {@code platform/error-handling.md} § Account (TASK-BE-462), which
 * {@code auth-api.md} now mirrors (TASK-BE-500).
 *
 * <p><b>Why this class exists.</b> Only {@code ACCOUNT_LOCKED} → 423 was pinned
 * (a {@code LoginControllerSliceTest} slice); the two statuses {@link
 * AuthExceptionHandler#handleAccountStatus} actually decides — {@code ACCOUNT_DORMANT}
 * → 423 and {@code ACCOUNT_DELETED} → 410 — had no test at all, which is precisely
 * how the iam spec tree drifted back to a blanket 403 without anything failing.
 */
class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    @DisplayName("ACCOUNT_LOCKED → 423 LOCKED (not 403)")
    void accountLocked() {
        assertStatus(handler.handleAccountLocked(new AccountLockedException()),
                HttpStatus.LOCKED, "ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("ACCOUNT_DORMANT → 423 LOCKED (not 403)")
    void accountDormant() {
        assertStatus(handler.handleAccountStatus(
                        new AccountStatusException("DORMANT", "ACCOUNT_DORMANT")),
                HttpStatus.LOCKED, "ACCOUNT_DORMANT");
    }

    @Test
    @DisplayName("ACCOUNT_DELETED → 410 GONE (not 403)")
    void accountDeleted() {
        assertStatus(handler.handleAccountStatus(
                        new AccountStatusException("DELETED", "ACCOUNT_DELETED")),
                HttpStatus.GONE, "ACCOUNT_DELETED");
    }

    /**
     * The {@code default} branch is a deliberate fail-loud: an account status the
     * service does not recognise must not be quietly rendered as a client error.
     */
    @Test
    @DisplayName("unrecognised account status → 500 (fail-loud, never a 4xx)")
    void unknownStatusFailsLoud() {
        ResponseEntity<ErrorResponse> r = handler.handleAccountStatus(
                new AccountStatusException("SOMETHING_NEW", "ACCOUNT_STATUS_UNKNOWN"));

        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "ACCOUNT_STATUS_UNKNOWN");
        assertThat(r.getStatusCode().is4xxClientError())
                .as("an unknown account status must not be reported as a client error")
                .isFalse();
    }

    /**
     * No account-status rejection is a 403. The blanket-403 shape is what TASK-BE-462
     * removed from the code and TASK-BE-500 removed from the specs; this guard is what
     * stops it coming back.
     */
    @Test
    @DisplayName("no account-status rejection is a 403")
    void neverForbidden() {
        assertThat(handler.handleAccountLocked(new AccountLockedException()).getStatusCode())
                .isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleAccountStatus(
                        new AccountStatusException("DORMANT", "ACCOUNT_DORMANT")).getStatusCode())
                .isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleAccountStatus(
                        new AccountStatusException("DELETED", "ACCOUNT_DELETED")).getStatusCode())
                .isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------------------------------------------------------------------
    // TASK-BE-512 — Layer-2 (per-email counter) 429 must carry Retry-After,
    // like the gateway's Layer-1 already does, and must NOT leak rate-limit
    // counters (remaining/reset) that would help an attacker calibrate.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("LOGIN_RATE_LIMITED → 429 carries a positive-integer Retry-After header")
    void loginRateLimitedCarriesRetryAfter() {
        ResponseEntity<ErrorResponse> r = handler.handleRateLimited(new LoginRateLimitedException(300L));

        assertStatus(r, HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED");
        String retryAfter = r.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter))
                .as("Retry-After must be a positive integer number of seconds")
                .isPositive()
                .isEqualTo(300L);
    }

    /**
     * rate-limiting.md forbids exposing remaining-count/reset headers on a 429 —
     * they help an attacker calibrate a credential-stuffing attack. Only
     * Retry-After is permitted.
     */
    @Test
    @DisplayName("LOGIN_RATE_LIMITED 429 does not leak X-RateLimit-*/remaining/reset headers")
    void loginRateLimitedDoesNotLeakCountHeaders() {
        ResponseEntity<ErrorResponse> r = handler.handleRateLimited(new LoginRateLimitedException(60L));

        HttpHeaders headers = r.getHeaders();
        assertThat(headers.keySet()).noneMatch(name ->
                name.toLowerCase().contains("ratelimit")
                        || name.equalsIgnoreCase("X-Remaining")
                        || name.toLowerCase().contains("reset"));
    }

    // ---------------------------------------------------------------------
    // TASK-MONO-350 — the three contradictions between code, contract and registry.
    // ---------------------------------------------------------------------

    /**
     * State mismatch is a malformed/forged callback, caught before any credential is
     * evaluated — a bad request, not an authentication failure. iam answered 401 while
     * ecommerce auth-service, the shared registry and RFC 6749 § 10.12 all said 400, and
     * the registry meanwhile asserted the two services emit this code "with identical
     * semantics" — a claim that was false precisely because of this handler.
     */
    @Test
    @DisplayName("INVALID_STATE → 400 (not 401): a forged/expired callback is a bad request")
    void invalidOAuthStateIsBadRequest() {
        assertStatus(handler.handleInvalidOAuthState(new InvalidOAuthStateException()),
                HttpStatus.BAD_REQUEST, "INVALID_STATE");
    }

    /**
     * 400 is correct here and is NOT what changed — answering 401 to an authenticated
     * user's password change reads as "your session died" and tends to log them out.
     * What changed is the code: it used to be {@code INVALID_CREDENTIALS}, the same code
     * a failed login emits at 401.
     */
    @Test
    @DisplayName("current-password mismatch → 400 CURRENT_PASSWORD_MISMATCH (no longer INVALID_CREDENTIALS)")
    void currentPasswordMismatchHasItsOwnCode() {
        assertStatus(handler.handleCurrentPasswordMismatch(new CurrentPasswordMismatchException()),
                HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH");
    }

    /**
     * The contract has always promised {@code 401 INVALID_CODE} and nothing ever emitted it:
     * every exchange failure was flattened into a 502, so a stale callback URL was reported
     * as an upstream outage.
     */
    @Test
    @DisplayName("rejected authorization code → 401 INVALID_CODE (not 502)")
    void rejectedCodeIsUnauthorizedNotBadGateway() {
        ResponseEntity<ErrorResponse> r =
                handler.handleOAuthCodeInvalid(new OAuthCodeInvalidException("Google rejected the code (400)"));

        assertStatus(r, HttpStatus.UNAUTHORIZED, "INVALID_CODE");
        assertThat(r.getStatusCode().is5xxServerError())
                .as("a rejected authorization code is the caller's fault; reporting 5xx pages an "
                        + "operator for a user error and invites retry-on-5xx to replay a single-use code")
                .isFalse();
    }

    /** A genuine provider failure must still be a 502 — the fix must not swallow real outages. */
    @Test
    @DisplayName("genuine provider failure → 502 PROVIDER_ERROR (unchanged)")
    void providerOutageStaysBadGateway() {
        assertStatus(handler.handleOAuthProviderError(new OAuthProviderException("Google 503")),
                HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR");
    }

    /**
     * The guard that would have caught all of this. TASK-MONO-348 established the rule in
     * finance/erp: a service that emits one {@code code} at two different statuses breaks
     * every client that branches on {@code code}. iam did exactly that with
     * {@code INVALID_CREDENTIALS} (401 on login, 400 on password change) and nothing failed,
     * because no test ever compared the handlers against each other.
     */
    @Test
    @DisplayName("no code leaves this service at two different HTTP statuses")
    void oneCodeOneStatus() {
        List<ResponseEntity<ErrorResponse>> everyHandler = List.of(
                handler.handleCredentialsInvalid(new CredentialsInvalidException()),
                handler.handleCurrentPasswordMismatch(new CurrentPasswordMismatchException()),
                handler.handleInvalidOAuthState(new InvalidOAuthStateException()),
                handler.handleOAuthCodeInvalid(new OAuthCodeInvalidException("rejected")),
                handler.handleOAuthProviderError(new OAuthProviderException("outage")),
                handler.handleAccountLocked(new AccountLockedException()),
                handler.handleAccountStatus(new AccountStatusException("DORMANT", "ACCOUNT_DORMANT")),
                handler.handleAccountStatus(new AccountStatusException("DELETED", "ACCOUNT_DELETED")),
                handler.handleAccountStatus(new AccountStatusException("SOMETHING_NEW", "ACCOUNT_STATUS_UNKNOWN")));

        Map<String, Set<HttpStatusCode>> statusesByCode = new LinkedHashMap<>();
        for (ResponseEntity<ErrorResponse> r : everyHandler) {
            statusesByCode
                    .computeIfAbsent(r.getBody().code(), k -> new LinkedHashSet<>())
                    .add(r.getStatusCode());
        }

        assertThat(statusesByCode).allSatisfy((code, statuses) -> assertThat(statuses)
                .as("code %s is emitted at more than one status: %s — a client branching on "
                        + "`code` cannot tell the two conditions apart", code, statuses)
                .hasSize(1));
    }

    private static void assertStatus(ResponseEntity<ErrorResponse> r,
                                     HttpStatus expected, String code) {
        assertThat(r.getStatusCode()).isEqualTo(expected);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }
}
