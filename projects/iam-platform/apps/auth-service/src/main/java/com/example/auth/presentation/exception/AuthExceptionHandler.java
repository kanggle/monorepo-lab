package com.example.auth.presentation.exception;

import com.example.auth.application.exception.*;
import com.example.auth.domain.credentials.PasswordPolicyViolationException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(CredentialsInvalidException.class)
    public ResponseEntity<ErrorResponse> handleCredentialsInvalid(CredentialsInvalidException e) {
        // HTTP response code is the platform-common canonical INVALID_CREDENTIALS
        // (TASK-MONO-246). NOTE: the login-failed EVENT failureReason enum keeps the
        // distinct value "CREDENTIALS_INVALID" (auth-events.md, consumed by
        // security-service) — the HTTP code and the event enum are separate contracts.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", "Invalid email or password"));
    }

    @ExceptionHandler(LoginTenantAmbiguousException.class)
    public ResponseEntity<ErrorResponse> handleLoginTenantAmbiguous(LoginTenantAmbiguousException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("LOGIN_TENANT_AMBIGUOUS",
                        "Email exists in multiple tenants. Please specify tenantId."));
    }

    @ExceptionHandler(TokenTenantMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTokenTenantMismatch(TokenTenantMismatchException e) {
        log.warn("Token tenant mismatch detected");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("TOKEN_TENANT_MISMATCH",
                        "Refresh token tenant does not match; rotation denied"));
    }

    /**
     * Password-change flow: the supplied <i>current password</i> field did not match.
     * Spring picks the most specific {@code @ExceptionHandler}, so this wins over
     * {@link #handleCredentialsInvalid}.
     *
     * <p><b>400 is deliberate and must stay 400</b> (TASK-MONO-350): the caller is already
     * authenticated — this is a request-field validation failure, not a failed login.
     * Answering 401 would read as "your session died" and typically makes a client log the
     * user out mid-password-change.
     *
     * <p>The <i>code</i>, however, used to be {@code INVALID_CREDENTIALS} — the same code
     * {@link #handleCredentialsInvalid} emits at 401. One code, two statuses, two meanings:
     * a client keying on {@code code} could not tell "your login failed" from "the current-password
     * field was wrong". Both facts are real and distinct, which is exactly why they get distinct
     * codes rather than one code with two statuses. The exception type was already named
     * {@link CurrentPasswordMismatchException}; only the emitted string lagged behind.
     */
    @ExceptionHandler(CurrentPasswordMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrentPasswordMismatch(CurrentPasswordMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("CURRENT_PASSWORD_MISMATCH", "Current password does not match"));
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ErrorResponse> handlePasswordPolicyViolation(PasswordPolicyViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("PASSWORD_POLICY_VIOLATION", e.getMessage()));
    }

    /**
     * Password-reset confirm flow (TASK-BE-109): unknown / expired / already-used
     * tokens, and the "credential row vanished between request and confirm" edge
     * case, all surface as the same 400 so the API does not leak which condition
     * triggered the rejection.
     */
    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handlePasswordResetTokenInvalid(
            PasswordResetTokenInvalidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("PASSWORD_RESET_TOKEN_INVALID",
                        "Password reset token is invalid or has expired"));
    }

    @ExceptionHandler(CredentialAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCredentialAlreadyExists(CredentialAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CREDENTIAL_ALREADY_EXISTS",
                        "Credential already exists for this account"));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException e) {
        // ACCOUNT_LOCKED → 423 LOCKED per platform/error-handling.md § Account (TASK-BE-462).
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ErrorResponse.of("ACCOUNT_LOCKED", "Account is locked"));
    }

    /**
     * Account-status login rejections map per carried error code, per
     * {@code platform/error-handling.md} § Account (TASK-BE-462) — not a blanket 403:
     * {@code ACCOUNT_DORMANT} → 423, {@code ACCOUNT_DELETED} → 410,
     * {@code ACCOUNT_STATUS_UNKNOWN} (and any unrecognised status) → 500.
     */
    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ErrorResponse> handleAccountStatus(AccountStatusException e) {
        HttpStatus status = switch (e.getErrorCode()) {
            case "ACCOUNT_DORMANT" -> HttpStatus.LOCKED;               // 423
            case "ACCOUNT_DELETED" -> HttpStatus.GONE;                 // 410
            default -> HttpStatus.INTERNAL_SERVER_ERROR;               // ACCOUNT_STATUS_UNKNOWN / unrecognised → 500
        };
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(LoginRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of("LOGIN_RATE_LIMITED", "Too many login attempts. Try again later."));
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("TOKEN_EXPIRED", "Token has expired"));
    }

    @ExceptionHandler(SessionRevokedException.class)
    public ResponseEntity<ErrorResponse> handleSessionRevoked(SessionRevokedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("SESSION_REVOKED", "Session has been revoked"));
    }

    @ExceptionHandler(TokenReuseDetectedException.class)
    public ResponseEntity<ErrorResponse> handleTokenReuseDetected(TokenReuseDetectedException e) {
        log.warn("Refresh token reuse detected, all sessions revoked");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("TOKEN_REUSE_DETECTED",
                        "Refresh token reuse detected; all sessions have been revoked"));
    }

    @ExceptionHandler(UnsupportedProviderException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedProvider(
            UnsupportedProviderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("UNSUPPORTED_PROVIDER", e.getMessage()));
    }

    /**
     * OAuth {@code state} missing / malformed / not matching the stored CSRF token
     * (RFC 6749 § 10.12).
     *
     * <p><b>400, not 401</b> (TASK-MONO-350). This is rejected before any credential is
     * evaluated: the callback request itself is malformed or forged. It is not an
     * authentication failure. 400 also matches ecommerce auth-service and the shared
     * registry, which were already 400 — iam was the lone outlier, and the registry's
     * claim that the two services emit this code "with identical semantics" was false
     * for as long as that was true.
     *
     * <p>The practical difference matters too: a 401 on a callback reads as "log in
     * again", which can drive a login → callback → 401 → login loop. A 400 stops it.
     */
    @ExceptionHandler(InvalidOAuthStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOAuthState(
            InvalidOAuthStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_STATE", "Invalid or expired OAuth state"));
    }

    @ExceptionHandler(InvalidOAuthRedirectUriException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOAuthRedirectUri(
            InvalidOAuthRedirectUriException e) {
        log.warn("Rejected OAuth request with redirect_uri not in allowlist");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_REDIRECT_URI", "Invalid redirect_uri"));
    }

    @ExceptionHandler(OAuthEmailRequiredException.class)
    public ResponseEntity<ErrorResponse> handleOAuthEmailRequired(
            OAuthEmailRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("EMAIL_REQUIRED", "Email is required for social login"));
    }

    /**
     * The provider rejected the authorization code itself (OAuth2 {@code invalid_grant}:
     * expired, already redeemed, or forged) — {@code 401 INVALID_CODE}, the response
     * {@code auth-api.md} has always promised and nothing ever emitted (TASK-MONO-350).
     *
     * <p>Until now every exchange failure was flattened into {@link OAuthProviderException}
     * and left as a <b>502</b>, so a user re-opening a stale callback URL was reported as an
     * upstream outage. That cost twice: 5xx-keyed alerting paged an operator for a user's
     * mistake, and retry-on-5xx clients retried a single-use code that can never succeed.
     *
     * <p>Logged at WARN, not ERROR — a stale code is routine, not an incident. This handler
     * must stay <i>above</i> {@link #handleOAuthProviderError} in intent: the exception is a
     * subclass, and Spring dispatches to the most specific handler.
     */
    @ExceptionHandler(OAuthCodeInvalidException.class)
    public ResponseEntity<ErrorResponse> handleOAuthCodeInvalid(OAuthCodeInvalidException e) {
        log.warn("OAuth authorization code rejected by provider: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CODE", "Authorization code is invalid or expired"));
    }

    /**
     * A genuine provider failure — 5xx, timeout, TLS/DNS, or a malformed token/user-info
     * response. Stays {@code 502 PROVIDER_ERROR}. Code-rejection (4xx on the token call) is
     * NOT routed here; see {@link #handleOAuthCodeInvalid}.
     */
    @ExceptionHandler(OAuthProviderException.class)
    public ResponseEntity<ErrorResponse> handleOAuthProviderError(
            OAuthProviderException e) {
        log.error("OAuth provider error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("PROVIDER_ERROR", "OAuth provider error"));
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAccountServiceUnavailable(AccountServiceUnavailableException e) {
        log.error("Account service unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("SERVICE_UNAVAILABLE", "A required service is temporarily unavailable"));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SESSION_NOT_FOUND", "Session not found"));
    }

    @ExceptionHandler(SessionOwnershipMismatchException.class)
    public ResponseEntity<ErrorResponse> handleSessionOwnership(SessionOwnershipMismatchException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("SESSION_OWNERSHIP_MISMATCH",
                        "Session does not belong to caller"));
    }
}
