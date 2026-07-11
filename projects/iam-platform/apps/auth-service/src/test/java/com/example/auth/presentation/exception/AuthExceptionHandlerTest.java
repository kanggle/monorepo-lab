package com.example.auth.presentation.exception;

import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.web.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
 * (a {@code LoginControllerTest} slice); the two statuses {@link
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

    private static void assertStatus(ResponseEntity<ErrorResponse> r,
                                     HttpStatus expected, String code) {
        assertThat(r.getStatusCode()).isEqualTo(expected);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }
}
