package com.example.admin.application.exception;

/**
 * Thrown when a submitted TOTP code fails verification in
 * {@code POST /api/admin/auth/2fa/verify}. Mapped to 401 INVALID_2FA_CODE.
 */
public class InvalidTwoFaCodeException extends RuntimeException {
    public InvalidTwoFaCodeException(String message) {
        super(message);
    }
}
