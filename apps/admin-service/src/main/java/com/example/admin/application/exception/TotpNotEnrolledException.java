package com.example.admin.application.exception;

/**
 * Raised when an operator attempts an operation that requires an existing
 * TOTP enrollment (e.g. {@code POST /api/admin/auth/2fa/recovery-codes/regenerate})
 * but no row exists in {@code admin_operator_totp}. Mapped to 404
 * {@code TOTP_NOT_ENROLLED} by {@code AdminExceptionHandler}.
 *
 * <p>Distinct from {@link InvalidTwoFaCodeException} (which signals a wrong
 * code at verification time) and {@link OperatorNotFoundException} (which
 * signals a missing operator row, not a missing TOTP enrollment).
 */
public class TotpNotEnrolledException extends RuntimeException {
    public TotpNotEnrolledException(String message) {
        super(message);
    }
}
